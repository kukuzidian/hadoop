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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class WhiteList {

    private static final Log LOG = LogFactory.getLog(WhiteList.class);
    private static Map<String, String> ipUserMap = new HashMap<>();
    private static HashSet<String> ipHashSet = new HashSet<>();
    private static final String SPLITSTR = " ";

    private static Lock lock = new ReentrantLock();

    public static void main(String[] args) {

        System.out.println(Thread.currentThread().getContextClassLoader().getResource(""));
        System.out.println(WhiteList.class.getResource(""));
        System.out.println(WhiteList.class.getResource("/"));
        System.out.println(ClassLoader.getSystemResource(""));
        System.out.println(System.getProperty("user.dir"));

        WhiteList rf = new WhiteList();
        TimeLoader tl = rf.new TimeLoader("/home/xiaoju/ip.txt");
        tl.start();
    }

    public WhiteList() {
        String currentPath = Thread.currentThread().getContextClassLoader()
            .getResource("").getPath();
        LOG.info(currentPath);
        String filePath = currentPath + "ip.txt";
        TimeLoader tl = this.new TimeLoader(filePath);
        tl.start();
        LOG.info(">>>>>>>>>>>>>>>>>>>>>>>ReadFile start...");
        print();
    }

    public WhiteList(String filePath) {
        TimeLoader tl = this.new TimeLoader(filePath);
        tl.start();
        LOG.info(">>>>>>>>>>>>>>>>>>>>>>>ReadFile start...");
        print();
    }


    public static void print() {
        try {
            LOG.info(">>print totalSize =  " + ipUserMap.size());
        } finally {
            lock.unlock();
        }
    }

    public static boolean contain(String ip, String username) {
        boolean flag = false;
        try {
            lock.lock();
            if (ipHashSet.size() == 0 || ipHashSet.contains(StringUtils.trimToEmpty(ip))) {
                flag = true;
            } else {
                if (ipUserMap.size() == 0) {
                    flag = true;
                } else {
                    if (ipUserMap.containsKey(StringUtils.trimToEmpty(ip))) {
                        if (StringUtils.startsWith(username, "appattempt")) {
                            flag = true;
                        } else {
                            if (ipUserMap.get(ip).contains(username)) {
                                flag = true;
                            } else {
                                LOG.info("Authorize  fail ,can not contain username ---------------1----username="
                                        + username + " from ip=" + ip + "----");
                                flag = false;
                            }
                        }
                    } else {
                        LOG.info("Authorize  fail ,can not contain ip---------------2----ip="
                                + ip + "----");
                        flag = false;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return flag;
    }

    class TimeLoader extends Thread {
        private String filePath = null;

        private String fileMd5Str = "";

        public TimeLoader() {
        }

        public TimeLoader(String filePath) {
            this.filePath = filePath;
            loadFile(this.filePath);
            print();
        }

        @Override
        public void run() {
            while (true) {
                loadFile(this.filePath);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.interrupted();
                }
            }
        }

        public String getMd5(File file) {
            FileInputStream fis = null;
            String flag = null;
            try {
                fis = new FileInputStream(file);
                flag = DigestUtils.md5Hex(fis);
            } catch (Exception e) {
                flag = null;
                LOG.error(e.getMessage());
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        LOG.error(e.getMessage());
                    } finally {
                        fis = null;
                    }
                }
            }
            return flag;
        }

        /**
         * load ip.txt
         *
         * @param filePath
         */
        public void loadFile(String filePath) {
            File file = new File(filePath);
            String tempStr = null;
            if (file != null && file.exists()) {
                tempStr = getMd5(file);
                if (tempStr == null) {
                    return;
                } else {
                    if (StringUtils.isEmpty(fileMd5Str)) {
                        fileMd5Str = tempStr;
                        LOG.info(">>>>>>>>>first md5=" + fileMd5Str);
                    } else {
                        if (tempStr.equals(fileMd5Str)) {
                            return;
                        } else {
                            fileMd5Str = tempStr;
                            LOG.info(">>>>>>>>>update md5=" + fileMd5Str);
                        }
                    }
                }

                FileReader fr = null;
                BufferedReader br = null;
                String tempLine = null;
                String[] arr = null;
                try {
                    fr = new FileReader(file);
                    br = new BufferedReader(fr);
                    try {
                        lock.lock();
                        ipUserMap.clear();
                        ipHashSet.clear();
                        while ((tempLine = br.readLine()) != null) {
                            arr = tempLine.split(SPLITSTR, 4);
                            if (arr != null && arr.length == 4) {
                                ipUserMap.put(arr[1].trim(), arr[2].trim());
                                if ("1".equals(arr[3].trim())) {
                                    ipHashSet.add(arr[1].trim());
                                }
                            } else {
                                LOG.error(">>>ip.txt contain error msg .");
                            }
                        }
                    } finally {
                        LOG.info(new Date() + " load file end."
                                + ipUserMap.size() + " |ipHashSet= " + ipHashSet.size());
                        lock.unlock();
                    }
                } catch (Exception e) {
                    LOG.error(e.getMessage());
                } finally {
                    try {
                        if (br != null) {
                            br.close();
                        }
                        if (fr != null) {
                            fr.close();
                        }
                    } catch (Exception e) {
                        LOG.error(e.getMessage());
                    } finally {
                        br = null;
                        fr = null;
                    }
                }
            } else {
                try {
                    lock.lock();
                    ipUserMap.clear();
                    ipHashSet.clear();
                    LOG.info("ip.txt is not exist ...path=" + filePath);
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
