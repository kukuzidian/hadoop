/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.security;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.HADOOP_SECURITY_GROUPS_MAPPING_REDIS_IP;

import org.apache.hadoop.util.ShutdownHookManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * A simple shell-based implementation of {@link GroupMappingServiceProvider}
 * that exec's the <code>groups</code> shell command to fetch the group
 * memberships of a given user.
 * 自定义权限管理实现类
 */
@InterfaceAudience.LimitedPrivate({"HDFS", "MapReduce"})
@InterfaceStability.Evolving
public class RedisBasedGroupsMapping implements GroupMappingServiceProvider, Configurable {
    private static final Log LOG = LogFactory.getLog(RedisBasedGroupsMapping.class);
    public static volatile String REDIS_IP = null;
    private volatile Configuration conf;
    private static volatile JedisPool pool = null;
    private static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final int SHUTDOWN_HOOK_PRIORITY = 0;

    static {
        ShutdownHookManager.get().addShutdownHook(new Runnable() {
            @Override
            public void run() {
                close();
            }
        }, SHUTDOWN_HOOK_PRIORITY);
    }

    /**
     * Returns list of groups for a user
     *
     * @param user get groups for this user
     * @return list of groups for a given user
     */
    @Override
    public List<String> getGroups(String user) throws IOException {
        return getGroupsFromRedis(user);
    }

    /**
     * Caches groups, no need to do that for this provider
     */
    @Override
    public void cacheGroupsRefresh() throws IOException {
        // does nothing in this provider of user to groups mapping
    }

    /**
     * Adds groups to cache, no need to do that for this provider
     *
     * @param groups unused
     */
    @Override
    public void cacheGroupsAdd(List<String> groups) throws IOException {
        // does nothing in this provider of user to groups mapping
    }

    /**
     * Get the current user's group list from Unix by running the command 'groups'
     * NOTE. For non-existing user it will return EMPTY list
     * @param user user name
     * @return the groups list that the <code>user</code> belongs to
     * @throws IOException if encounter any error when running the command
     */
    private List<String> getGroupsFromRedis(final String user) throws IOException {
        ArrayList<String> result =  new ArrayList<>();
        Jedis jedis = null;
        try {
            if (pool == null) {
                initRedisPool();
            }
            lock.readLock().lock();
            jedis = pool.getResource();
            Set<String> set = jedis.smembers("u_" + user);
            if (set != null) {
                for (String g : set) {
                    LOG.info("search group from redis: " + g);
                }
            }
            result = new ArrayList<>(set);
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
        result.add(user);
        return result;
    }

    @Override
    public void setConf(Configuration conf) {
        if (this.conf == null) {
            this.conf = conf;
        } else {
            this.conf = conf;
            refreshRedisPool();
        }
    }

    public void initRedisPool() {
        try {
            lock.writeLock().lock();
            if (pool == null) {
                int maxTotal = conf.getInt("hadoop.security.group.mapping.redis.maxTotal", 500);
                String redisIp = conf.get(HADOOP_SECURITY_GROUPS_MAPPING_REDIS_IP);
                REDIS_IP = redisIp;
                JedisPoolConfig config = new JedisPoolConfig();
                config.setMaxTotal(maxTotal);
                config.setMinIdle(10);
                pool = new JedisPool(config, REDIS_IP, 6379, 0);
            }
        } catch (Exception ex) {
            LOG.error(ex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void refreshRedisPool() {
        String redisIp = conf.get(HADOOP_SECURITY_GROUPS_MAPPING_REDIS_IP);
        if (redisIp == null || redisIp.equals("")) {
            if (pool != null) {
                LOG.info("Redis IP is null, destroy redis pool");
                try {
                    pool.destroy();
                } catch (Exception ex) {
                    LOG.error(ex);
                } finally {
                    pool = null;
                }
            }
            return;
        }

        boolean isChanged = false;
        if (REDIS_IP == null || !REDIS_IP.equals(redisIp)) {
            REDIS_IP = redisIp;
            isChanged = true;
        }
        int maxTotal = conf.getInt("hadoop.security.group.mapping.redis.maxTotal", 500);

        if (isChanged) {
            LOG.info("Init redis pool, ip=" + REDIS_IP);
            JedisPool oldPool = null;
            try {
                JedisPoolConfig config = new JedisPoolConfig();
                config.setMaxTotal(maxTotal);
                config.setMinIdle(10);
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
            } catch (Exception ex) {
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

    @Override
    public Configuration getConf() {
        return conf;
    }

    public static void close() {
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
}