package org.apache.hadoop.externaltrash;

import junit.framework.TestCase;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.FS_TRASH_CHECKPOINT_INTERVAL_KEY;
import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.FS_TRASH_INTERVAL_KEY;

/**
 * Created by user on 16/11/2016.
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

    static String writeFile(FileSystem fileSys, Path name, int fileSize)
            throws IOException {
        final long seed = 0xDEADBEEFL;
        // Create and write a file that contains three blocks of data
        FSDataOutputStream stm = fileSys.create(name);
        byte[] buffer = new byte[fileSize];
        Random rand = new Random(seed);
        rand.nextBytes(buffer);
        stm.write(buffer);
        stm.close();
        return new String(buffer);
    }

    public void testExternalTrash() throws Exception {
        Configuration conf = new Configuration();
        // Trash with 12 second deletes and 6 seconds checkpoints
        conf.set(FS_TRASH_INTERVAL_KEY, "0.2"); // 12 seconds
        conf.setClass("fs.file.impl", TestLFS.class, FileSystem.class);
        conf.set(FS_TRASH_CHECKPOINT_INTERVAL_KEY, "0.1"); // 6 seconds
        FileSystem fs = FileSystem.getLocal(conf);
        conf.set("fs.defaultFS", fs.getUri().toString());

        ExternalTrash externalTrash = new ExternalTrash(conf);
        Thread trashTread = new Thread(externalTrash);
        trashTread.start();


        // First create a new directory with mkdirs
        Path myPath = new Path(TEST_DIR, "test/mkdirs");
        mkdir(fs, myPath);

        // Create user A\B\C's home directory
        Path homeDir = fs.getHomeDirectory();
        Path trashDirA = new Path(homeDir.getParent(), "A/.Trash/Current");
        Path trashDirB = new Path(homeDir.getParent(), "B/.Trash/Current");
        Path trashDirC = new Path(homeDir.getParent(), "C/.Trash/Current");

        mkdir(fs, trashDirA);
        mkdir(fs, trashDirB);
        mkdir(fs, trashDirC);

        int fileIndex = 0;
        Set<String> checkpointsA = new HashSet<String>();
        Set<String> checkpointsB = new HashSet<String>();
        Set<String> checkpointsC = new HashSet<String>();
        while (true)  {
            // Create a file with a new name and remove to trash (mock)
            Path myFile = new Path(TEST_DIR, "test/mkdirs/myFile" + fileIndex++);
            writeFile(fs, myFile, 10);
            fs.rename(myFile, trashDirA);

            myFile = new Path(TEST_DIR, "test/mkdirs/myFile" + fileIndex++);
            writeFile(fs, myFile, 10);
            fs.rename(myFile, trashDirB);

            myFile = new Path(TEST_DIR, "test/mkdirs/myFile" + fileIndex++);
            writeFile(fs, myFile, 10);
            fs.rename(myFile, trashDirC);


            FileStatus filesA[] = fs.listStatus(trashDirA.getParent());
            // Scan files in .Trash and add them to set of checkpoints
            for (FileStatus file : filesA) {
                String fileName = file.getPath().getName();
                checkpointsA.add(fileName);
            }

            FileStatus filesB[] = fs.listStatus(trashDirB.getParent());
            // Scan files in .Trash and add them to set of checkpoints
            for (FileStatus file : filesB) {
                String fileName = file.getPath().getName();
                checkpointsB.add(fileName);
            }

            FileStatus filesC[] = fs.listStatus(trashDirC.getParent());
            // Scan files in .Trash and add them to set of checkpoints
            for (FileStatus file : filesC) {
                String fileName = file.getPath().getName();
                checkpointsC.add(fileName);
            }

            if (checkpointsB.size() == 6) {
                Assert.assertTrue(checkpointsA.size() == checkpointsB.size());
                Assert.assertTrue(checkpointsC.size() == checkpointsA.size());

                Assert.assertTrue(filesA.length < filesB.length);
                Assert.assertTrue(filesC.length < filesA.length);
                break;
            }

            Thread.sleep(5000);
        }
        trashTread.interrupt();
        trashTread.join();
    }

    /**
     * @see TestCase#tearDown()
     */
    @Override
    protected void tearDown() throws IOException {
        File trashDir = new File(TEST_DIR.toUri().getPath());
        if (trashDir.exists() && !FileUtil.fullyDelete(trashDir)) {
            throw new IOException("Cannot remove data directory: " + trashDir);
        }
    }

}
