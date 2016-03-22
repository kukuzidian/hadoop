package org.apache.hadoop.hdfs.server.namenode;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



import java.io.IOException;


import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.fail;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.hadoop.conf.ReconfigurationException;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.fs.permission.PermissionStatus;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import static org.apache.hadoop.hdfs.DFSConfigKeys.FS_PROTECTED_DIRECTORIES;

public class TestNameNodeReconfigure {

    public TestNameNodeReconfigure() {
    }

    public static final Log LOG = LogFactory
            .getLog(TestNameNodeReconfigure.class);

    private MiniDFSCluster cluster;

    @Before
    public void setUp() throws IOException {
        Configuration conf = new HdfsConfiguration();
        cluster = new MiniDFSCluster.Builder(conf).build();
    }

    /**
     * Test that we can modify configuration properties.
     */
    @Test
    public void testReconfigure() throws ReconfigurationException, IOException {
        // change properties
        cluster.getNameNode().reconfigureProperty(FS_PROTECTED_DIRECTORIES, "/user");
        // try to delete protected dirs
        try {
            cluster.getNameNode().namesystem.mkdirs("/user/sub1" ,
                    PermissionStatus.createImmutable("test", "test",
                    FsPermission.createImmutable((short)700)),
                    true);
            cluster.getNameNode().namesystem.delete("/user", true, false);
            fail("AccessControlException expected");
        } catch (AccessControlException ex) {
        }

    }

    @After
    public void shutDown() throws IOException {
        if (cluster != null) {
            cluster.shutdown();
        }
    }
}
