package com.altamiracorp.lumify.storm.twitter;

import com.altamiracorp.lumify.twitter.TwitterConstants;
import backtype.storm.Config;
import backtype.storm.generated.StormTopology;
import backtype.storm.topology.BoltDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import com.altamiracorp.lumify.storm.BaseFileSystemSpout;
import com.altamiracorp.lumify.storm.HdfsFileSystemSpout;
import com.altamiracorp.lumify.storm.StormRunnerBase;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

import java.util.Arrays;
import java.util.List;

public class StormRunner extends StormRunnerBase {
    /**
     * The tweet processing topology name.
     */
    private static final String TOPOLOGY_NAME = "lumify-twitter";

    /**
     * The Twitter query spout name.
     */
    private static final String QUERY_SPOUT_NAME = "twitterStreamSpout";

    /**
     * The Twitter file spout name.
     */
    private static final String FILE_SPOUT_NAME = "twitterFileSpout";

    /**
     * The Twitter file processing bolt name.
     */
    private static final String FILE_PROC_BOLT_NAME = "twitterFileProcBolt";

    /**
     * The Tweet processing bolt name.
     */
    private static final String TWEET_PROC_BOLT_NAME = "tweetProcBolt";

    private static final String MENTION_BOLT_NAME = "tweetMentionBolt";
    private static final String HASHTAG_BOLT_NAME = "tweetHashtagBolt";
    private static final String URL_BOLT_NAME = "tweetUrlBolt";
    private static final String PROFILE_PHOTO_BOLT_NAME = "tweetProfilePhotoBolt";
    private static final String TWEET_HIGHLIGHT_BOLT_NAME = "tweetHighlightSubmissionBolt";

    /**
     * The default HDFS root data directory: "/lumify/data"
     */
    private static final String DEFAULT_HDFS_DATA_ROOT = "/lumify/data";
    /**
     * The default subdirectory in HDFS where raw tweets are found: "rawTweet";
     */
    private static final String DEFAULT_HDFS_TWEET_SUBDIR = "rawTweet";

    /**
     * Command line option disabling the streaming query spout.
     * <code>--no-query</code>
     */
    private static final String CMD_OPT_NO_QUERY = "no-query";

    /**
     * Command line option disabling the HDFS file spout.
     * <code>--no-file</code>
     */
    private static final String CMD_OPT_NO_FILE = "no-file";

    /**
     * The HDFS root data directory path property.  If not found
     * in the configuration properties, this will default to
     * DEFAULT_HDFS_DATA_ROOT.
     */
    private static final String HDFS_ROOT_PATH_PROPERTY = "twitter.hdfs.dataRoot";

    /**
     * The property key for the HDFS subdirectory containing the raw tweet files
     * to process.  If not found in the configuration properties, this will default
     * to DEFAULT_HDFS_TWEET_SUBDIR.
     */
    private static final String HDFS_TWEET_SUBDIR_PROPERTY = "twitter.hdfs.tweetPath";

    public static void main(String[] args) throws Exception {
        int res = new StormRunner().run(args);
        if (res != 0) {
            System.exit(res);
        }
    }

    /**
     * The HDFS root data path.
     */
    private String hdfsDataRoot;

    /**
     * The HDFS tweet subdirectory.
     */
    private String hdfsTweetSubdir;

    /**
     * If true, the streaming query topology will be created.
     */
    private boolean startQuerySpout = true;

    /**
     * If true, the HDFS file topology will be created.
     */
    private boolean startFileSpout = true;

    @Override
    protected Options getOptions() {
        Options opts = super.getOptions();

        opts.addOption(
                OptionBuilder
                        .withLongOpt(CMD_OPT_NO_QUERY)
                        .withDescription("Do not start streaming query spout.")
                        .create()
        );

        opts.addOption(
                OptionBuilder
                        .withLongOpt(CMD_OPT_NO_FILE)
                        .withDescription("Do not start HDFS file processing spout.")
                        .create()
        );

        return opts;
    }

    @Override
    protected String getTopologyName() {
        return TOPOLOGY_NAME;
    }

    @Override
    protected void beforeCreateTopology(CommandLine cmd, Config conf) throws Exception {
        super.beforeCreateTopology(cmd, conf);
        // start the query spout unless --no-query option is present
        startQuerySpout = !cmd.hasOption(CMD_OPT_NO_QUERY);
        // start the file spout unless --no-file option is present
        startFileSpout = !cmd.hasOption(CMD_OPT_NO_FILE);
        if (!(startQuerySpout || startFileSpout)) {
            throw new IllegalStateException("At least one spout must be started.");
        }
        if (startFileSpout) {
            // only configure HDFS properties if necessary
            String rootProp = (String) conf.get(HDFS_ROOT_PATH_PROPERTY);
            String subDirProp = (String) conf.get(HDFS_TWEET_SUBDIR_PROPERTY);
            hdfsDataRoot = rootProp != null ? rootProp.trim() : DEFAULT_HDFS_DATA_ROOT;
            hdfsTweetSubdir = (subDirProp != null && !subDirProp.trim().isEmpty()) ? subDirProp.trim() : DEFAULT_HDFS_TWEET_SUBDIR;
            if (!hdfsTweetSubdir.startsWith("/")) {
                hdfsTweetSubdir = "/" + hdfsTweetSubdir;
            }
            conf.put(BaseFileSystemSpout.DATADIR_CONFIG_NAME, hdfsDataRoot);
        }
    }

    @Override
    public StormTopology createTopology(int parallelismHint) {
        TopologyBuilder builder = new TopologyBuilder();
        BoltDeclarer tweetBolt = builder.setBolt(TWEET_PROC_BOLT_NAME, new TwitterStreamingBolt(), parallelismHint);
        builder.setBolt(MENTION_BOLT_NAME, new TwitterMentionEntityCreationBolt(MENTION_BOLT_NAME), parallelismHint)
                .shuffleGrouping(TWEET_PROC_BOLT_NAME);
        builder.setBolt(HASHTAG_BOLT_NAME, new TwitterHashtagEntityCreationBolt(HASHTAG_BOLT_NAME), parallelismHint)
                .shuffleGrouping(TWEET_PROC_BOLT_NAME);
        builder.setBolt(URL_BOLT_NAME, new TwitterUrlEntityCreationBolt(URL_BOLT_NAME), parallelismHint)
                .shuffleGrouping(TWEET_PROC_BOLT_NAME);
        builder.setBolt(PROFILE_PHOTO_BOLT_NAME, new TwitterProfilePhotoBolt(PROFILE_PHOTO_BOLT_NAME), parallelismHint)
                .shuffleGrouping(PROFILE_PHOTO_BOLT_NAME);
        List<String> joinBoltIds = Arrays.asList(
                MENTION_BOLT_NAME,
                HASHTAG_BOLT_NAME,
                URL_BOLT_NAME,
                PROFILE_PHOTO_BOLT_NAME
        );
        builder.setBolt(TWEET_HIGHLIGHT_BOLT_NAME, new TwitterTweetHighlightSubmitBolt(joinBoltIds), parallelismHint)
                .fieldsGrouping(MENTION_BOLT_NAME, new Fields(TwitterConstants.TWEET_VERTEX_ID_FIELD))
                .fieldsGrouping(HASHTAG_BOLT_NAME, new Fields(TwitterConstants.TWEET_VERTEX_ID_FIELD))
                .fieldsGrouping(URL_BOLT_NAME, new Fields(TwitterConstants.TWEET_VERTEX_ID_FIELD))
                .fieldsGrouping(PROFILE_PHOTO_BOLT_NAME, new Fields(TwitterConstants.TWEET_VERTEX_ID_FIELD));

        if (startQuerySpout) {
            builder.setSpout(QUERY_SPOUT_NAME, new TwitterStreamSpout(), 1);
            tweetBolt.shuffleGrouping(QUERY_SPOUT_NAME);
        }
        if (startFileSpout) {
            builder.setSpout(FILE_SPOUT_NAME, new HdfsFileSystemSpout(hdfsTweetSubdir), 1);
            builder.setBolt(FILE_PROC_BOLT_NAME, new TwitterFileProcessingBolt(), parallelismHint)
                    .shuffleGrouping(FILE_SPOUT_NAME);
            tweetBolt.shuffleGrouping(FILE_PROC_BOLT_NAME);
        }
        return builder.createTopology();
    }
}
