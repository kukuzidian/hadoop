package org.apache.hadoop.externaltrash;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;


/**
 * Created by user on 15/11/2016.
 */
public class TestExternalTrash extends TestCase {

    private final static Path TEST_DIR =
            new Path(new File(System.getProperty("test.build.data","/tmp")
            ).toURI().toString().replace(' ', '+'), "testExternalTrash");


    static class TestLFS extends LocalFileSystem {
        Path home;
        TestLFS() {
            this(new Path(TEST_DIR, "user/test"));
        }
        TestLFS(Path home) {
            super();
            this.home = home;
        }
        @Override
        public Path getHomeDirectory() {
            return home;
        }
    }

    protected static Path mkdir(FileSystem fs, Path p) throws IOException {
        assertTrue(fs.mkdirs(p));
        assertTrue(fs.exists(p));
        assertTrue(fs.getFileStatus(p).isDirectory());
        return p;
    }
}
