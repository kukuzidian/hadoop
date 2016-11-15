package org.apache.hadoop.externaltrash;

import com.google.common.base.Strings;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.*;

import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.FS_TRASH_INTERVAL_KEY;

/**
 * A external trash utility to create and delete checkpoints of different user's trash
 * with different interval.
 */
public class ExternalTrash {

    private static final Log LOG = LogFactory.getLog(ExternalTrash.class);

    private static final String CONF_FILENAME = "externaltrash.xml";
    private static final String CONF_PREFIX = "fs.user.";
    private static final String CONF_SUFFIX = ".trash.interval";

    private final Configuration conf;

    private Map<String, Float> user2Interval = new HashMap<>();

    public ExternalTrash(Configuration conf) {
        this.conf = conf;

        init();
    }

    /**
     * Loop configurations and collect username and its trash-interval.
     */
    private void init() {

        for (Map.Entry<String, String> entry : this.conf) {
            if (!entry.getKey().startsWith(CONF_PREFIX) ||
                    !entry.getKey().endsWith(CONF_SUFFIX)) {
                continue;
            }

            String user = entry.getKey().replace(CONF_PREFIX, "").replace(CONF_SUFFIX, "");
            if (Strings.isNullOrEmpty(user)) {
                continue;
            }

            float interval = Float.parseFloat(entry.getValue());
            LOG.info(user + "'s trash interval is " + interval);
            this.user2Interval.put(user, interval);
        }

    }

    /**
     * Scan home's parent directory and process user's trash one by one.
     */
    public void run() throws IOException {

        try(FileSystem fs = FileSystem.get(this.conf)){
            Path homesParent = fs.getHomeDirectory().getParent();

            FileStatus[] homes = fs.listStatus(homesParent);         // list all home dirs

            for (FileStatus home : homes) {         // dump each trash
                if (!home.isDirectory())
                    continue;

                Configuration copyConf = new Configuration(this.conf);
                String user = home.getPath().getName();
                if (this.user2Interval.containsKey(user)) {
                    copyConf.setFloat(FS_TRASH_INTERVAL_KEY, this.user2Interval.get(user));
                }

                try {
                    TrashPolicy trash = TrashPolicy.getInstance(copyConf, fs, home.getPath());
                    trash.deleteCheckpoint();
                    trash.createCheckpoint();
                } catch (IOException e) {
                    LOG.warn("Trash caught: " + e + ". Skipping " + home.getPath() + ".");
                }
            }

        }
    }


    public static void main(String[] args) throws IOException {

        Configuration conf = new Configuration();
        conf.addResource(CONF_FILENAME);

        ExternalTrash externalTrash = new ExternalTrash(conf);

        externalTrash.run();
    }


}
