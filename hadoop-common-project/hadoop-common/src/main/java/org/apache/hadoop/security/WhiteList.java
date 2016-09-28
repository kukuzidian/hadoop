/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.security;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.util.Time;
import org.apache.hadoop.util.Timer;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.HADOOP_SECURITY_GROUPS_MAPPING_REDIS_IP;
import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.HADOOP_SECURITY_USE_WHITELIST;

public class WhiteList {

    private static final Log LOG = LogFactory.getLog(WhiteList.class);
    private volatile Configuration conf;
    private static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Timer timer;
    private long cacheTimeout;
    public static volatile String REDIS_IP = null;
    private static volatile JedisPool pool = null;
    private static WhiteList globalWhiteList = null;
    private AtomicBoolean isEnabled = new AtomicBoolean(false);

    private final Map<String, CachedSet> cache =
        new ConcurrentHashMap<String, CachedSet>();

    public WhiteList(Configuration conf) {
        this.conf = conf;
        this.timer = new Timer();
        this.cacheTimeout =
                conf.getLong(CommonConfigurationKeys.HADOOP_SECURITY_GROUPS_CACHE_SECS,
                        CommonConfigurationKeys.HADOOP_SECURITY_GROUPS_CACHE_SECS_DEFAULT) * 1000;
        initRedisPool();
        LOG.info(">>>>>>>>>>>>>>>>>>>>>>>WhiteList start...");
    }

    public boolean isEnabled() {
        return isEnabled.get();
    }

    /**
     * Get the groups being used to map user-to-groups.
     * @param conf
     * @return the groups being used to map user-to-groups.
     */
    public static synchronized WhiteList getWhiteList(Configuration conf) {
        if(globalWhiteList == null) {
            if(LOG.isDebugEnabled()) {
                LOG.debug(" Creating new Groups object");
            }
            globalWhiteList = new WhiteList(conf);
        }
        return globalWhiteList;
    }

    public void refresh(Configuration conf) {
        this.conf = conf;
        initRedisPool();
    }

    public void initRedisPool() {
        String redisIp = conf.get(HADOOP_SECURITY_GROUPS_MAPPING_REDIS_IP);
        boolean enableWhiteList = conf.getBoolean(HADOOP_SECURITY_USE_WHITELIST, false);
        if (!enableWhiteList || redisIp == null || redisIp.equals("")) {
            close();
            return;
        }

        boolean isChanged = false;
        if (REDIS_IP == null || !REDIS_IP.equals(redisIp)) {
            REDIS_IP = redisIp;
            isChanged = true;
        }
        int maxTotal = conf.getInt("hadoop.security.group.mapping.redis.maxTotal", 500);
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(maxTotal);
        config.setMinIdle(10);
        if (isChanged) {
            LOG.info("Init redis pool, ip=" + REDIS_IP);
            JedisPool oldPool = null;
            try {
                if (pool != null) {
                    JedisPool newPool = new JedisPool(config, REDIS_IP, 6379, 0);
                    lock.writeLock().lock();
                    oldPool = pool;
                    pool = newPool;
                    newPool = null;
                } else {
                    lock.writeLock().lock();
                    pool = new JedisPool(config, REDIS_IP, 6379, 0);
                }
                isEnabled.getAndSet(true);
            } catch(Exception ex) {
                LOG.error(ex);
            } finally {
                lock.writeLock().unlock();
                try {
                    if (oldPool != null) {
                        oldPool.destroy();
                    }
                } catch (Exception ex) {
                    LOG.error(ex);
                } finally {
                    oldPool = null;
                }
            }
        }
    }

    /**
     * Queries impl for groups belonging to the user. This could involve I/O and take a while.
     */
    public static Set<String> getKeyFromRedis(String key) {
        Jedis jedis = null;
        Set<String> result = null;
        try {
            lock.readLock().lock();
            jedis = pool.getResource();
            result = jedis.smembers(key);
        } catch(Exception e) {
            LOG.error(e);
        } finally {
            lock.readLock().unlock();
            try {
                if (jedis != null) {
                    jedis.close();
                }
                jedis = null;
            } catch (Exception e) {
                LOG.error(e);
            }
        }
        return result;
    }

    public Set<String> getFromCache(String ip) {
        Set<String> result = null;
        String key = "ip_" + StringUtils.trimToEmpty(ip);
        try {
            // Return cached value if available
            CachedSet groups = cache.get(key);
            long now = Time.now();
            // if cache has a value and it hasn't expired
            if (groups != null && (groups.getTimestamp() + cacheTimeout > now)) {
                return groups.getGroups();
            }

            Set<String> set = getKeyFromRedis(key);
            if (set == null && groups != null) {
                return groups.getGroups();
            }

            // Create and cache user's groups
            groups = new CachedSet(set);
            cache.put(ip, groups);
            return groups.getGroups();
        } catch (Exception ex) {
            LOG.error(ex);
            result = getKeyFromRedis(key);
        }
        return result;
    }

    public boolean contain(String ip, String username) {
        boolean flag = false;
        try {
            if (ip == null || StringUtils.trimToEmpty(ip).equals("")) return true;

            Set<String> result = getFromCache(ip);
            if (result == null || result.size() == 0) {
                LOG.info("Authorize fail ,do not contain ip, ip=" + ip + "----");
                flag = false;
            } else {
                if (result.contains("*") || StringUtils.startsWith(username, "appattempt")
                    || result.contains(username)) {
                    flag = true;
                } else {
                    LOG.info("Authorize fail ,do not contain username ---------------1----username="
                            + username + " from ip=" + ip + "----");
                    flag = false;
                }
            }
        } catch (Exception e) {
            LOG.error(e);
        }
        return flag;
    }

    public void close() {
        isEnabled.getAndSet(false);
        if (pool != null) {
            LOG.info("Destroy redis pool");
            try {
                pool.destroy();
            } catch (Exception ex) {
                LOG.error(ex);
            } finally {
                pool = null;
                REDIS_IP = null;
            }
        }
    }

    /**
     * Class to hold the cached groups
     */
    public static class CachedSet {
        final long timestamp;
        final Set<String> groups;

        /**
         * Create and initialize group cache
         */
        CachedSet(Set<String> groups) {
            this.groups = groups;
            this.timestamp = Time.now();
        }

        /**
         * Returns time of last cache update
         *
         * @return time of last cache update
         */
        public long getTimestamp() {
            return timestamp;
        }

        /**
         * Get list of cached groups
         *
         * @return cached groups
         */
        public Set<String> getGroups() {
            return groups;
        }
    }
}
