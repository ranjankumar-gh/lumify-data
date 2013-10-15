package com.altamiracorp.lumify.storm;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.StormTopology;
import backtype.storm.spout.Scheme;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.utils.Utils;
import com.altamiracorp.lumify.cmdline.CommandLineBase;
import com.altamiracorp.lumify.config.ConfigurationHelper;
import com.altamiracorp.lumify.model.AccumuloSession;
import com.altamiracorp.lumify.storm.contentTypeSorter.ContentTypeSorterBolt;
import com.altamiracorp.lumify.storm.document.DocumentBolt;
import com.altamiracorp.lumify.storm.image.ImageBolt;
import com.altamiracorp.lumify.storm.structuredDataExtraction.StructuredDataBolt;
import com.altamiracorp.lumify.storm.termExtraction.TermExtractionBolt;
import com.altamiracorp.lumify.storm.textHighlighting.ArtifactHighlightingBolt;
import com.altamiracorp.lumify.storm.video.VideoBolt;
import com.altamiracorp.lumify.storm.video.VideoPreviewBolt;
import org.apache.accumulo.core.util.CachedConfiguration;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storm.kafka.KafkaConfig;
import storm.kafka.KafkaSpout;
import storm.kafka.SpoutConfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class StormRunner extends CommandLineBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(StormRunner.class);
    public static final String LOCAL_CONFIG_KEY = "local";
    public static final String TOPOLOGY_NAME = "lumify";
    private boolean isDone;

    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(CachedConfiguration.getInstance(), new StormRunner(), args);
        if (res != 0) {
            System.exit(res);
        }
    }

    public StormRunner() {
        initFramework = true;
    }

    @Override
    protected Options getOptions() {
        Options opts = super.getOptions();

        opts.addOption(
                OptionBuilder
                        .withLongOpt("datadir")
                        .withDescription("Location of the data directory")
                        .hasArg()
                        .create()
        );

        opts.addOption(
                OptionBuilder
                        .withLongOpt("local")
                        .withDescription("Run local")
                        .create()
        );

        return opts;
    }

    @Override
    protected int run(CommandLine cmd) throws Exception {
        String dataDir = cmd.getOptionValue("datadir");
        boolean isLocal = cmd.hasOption("local");

        Config conf = new Config();
        conf.put("topology.kryo.factory", "com.altamiracorp.lumify.storm.DefaultKryoFactory");
        for (Map.Entry<Object, Object> configEntry : getConfiguration().getProperties().entrySet()) {
            conf.put(configEntry.getKey().toString(), configEntry.getValue());
        }
        conf.put(BaseFileSystemSpout.DATADIR_CONFIG_NAME, "/lumify/data");
        conf.setDebug(false);
        conf.setNumWorkers(2);

        if (isLocal) {
            copyDataFilesToHdfs(conf, dataDir);
        }

        StormTopology topology = createTopology();
        LOGGER.info("Submitting topology '" + TOPOLOGY_NAME + "'");
        if (isLocal) {
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(TOPOLOGY_NAME, conf, topology);

            // TODO: how do we know when we are done?
            while (!isDone) {
                Utils.sleep(100);
            }
            cluster.killTopology("local");
            cluster.shutdown();
        } else {
            StormSubmitter.submitTopology(TOPOLOGY_NAME, conf, topology);
        }

        return 0;
    }

    private void copyDataFilesToHdfs(Config stormConf, String dataDir) throws URISyntaxException, IOException, InterruptedException {
        File dataDirFile = new File(dataDir);
        String hdfsRootDir = (String) stormConf.get(AccumuloSession.HADOOP_URL);
        Configuration conf = ConfigurationHelper.createHadoopConfigurationFromMap(stormConf);

        FileSystem hdfsFileSystem = FileSystem.get(new URI(hdfsRootDir), conf, "hadoop");

        for (File f : dataDirFile.listFiles()) {
            Path srcPath = new Path(f.getAbsolutePath());
            if (srcPath.getName().startsWith(".")) {
                continue;
            }
            Path dstPath = new Path("/lumify/data/unknown/" + srcPath.getName());
            hdfsFileSystem.copyFromLocalFile(false, true, srcPath, dstPath);
        }
    }

    public StormTopology createTopology() {
        TopologyBuilder builder = new TopologyBuilder();
        createContentTypeSorterTopology(builder);
        createVideoTopology(builder);
        createImageTopology(builder);
        createDocumentTopology(builder);
        createStructuredDataTopology(builder);
        createTextTopology(builder);
        createArtifactHighlightingTopology(builder);
        createProcessedVideoTopology(builder);

        return builder.createTopology();
    }

    private TopologyBuilder createContentTypeSorterTopology(TopologyBuilder builder) {
        builder.setSpout("fileSorter", new HdfsFileSystemSpout("/unknown"), 1);
        builder.setBolt("contentTypeSorterBolt", new ContentTypeSorterBolt(), 1)
                .shuffleGrouping("fileSorter");
        return builder;
    }

    private void createImageTopology(TopologyBuilder builder) {
        String queueName = "image";
        builder.setSpout(queueName, new HdfsFileSystemSpout("/image"), 1);
        builder.setBolt(queueName + "-bolt", new ImageBolt(), 1)
                .shuffleGrouping(queueName);
    }

    private void createVideoTopology(TopologyBuilder builder) {
        String queueName = "video";
        builder.setSpout(queueName, new HdfsFileSystemSpout("/video"), 1);
        builder.setBolt(queueName + "-bolt", new VideoBolt(), 1)
                .shuffleGrouping(queueName);
    }

    private void createDocumentTopology(TopologyBuilder builder) {
        String queueName = "document";
        builder.setSpout(queueName, new HdfsFileSystemSpout("/document"), 1);
        builder.setBolt(queueName + "-bolt", new DocumentBolt(), 1)
                .shuffleGrouping(queueName);
    }

    private void createStructuredDataTopology(TopologyBuilder builder) {
        String queueName = "structuredData";
        builder.setSpout(queueName, new HdfsFileSystemSpout("/structuredData"), 1);
        builder.setBolt(queueName + "-bolt", new StructuredDataBolt(), 1)
                .shuffleGrouping(queueName);
    }

    private void createTextTopology(TopologyBuilder builder) {
        SpoutConfig spoutConfig = createSpoutConfig("text", null);
        builder.setSpout("text", new KafkaSpout(spoutConfig), 1);
        builder.setBolt("textTermExtractionBolt", new TermExtractionBolt(), 1)
                .shuffleGrouping("text");
    }

    private void createArtifactHighlightingTopology(TopologyBuilder builder) {
        SpoutConfig spoutConfig = createSpoutConfig("artifactHighlight", null);
        builder.setSpout("artifactHighlightSpout", new KafkaSpout(spoutConfig), 1);
        builder.setBolt("artifactHighlightBolt", new ArtifactHighlightingBolt(), 1)
                .shuffleGrouping("artifactHighlightSpout");
    }

    private void createProcessedVideoTopology(TopologyBuilder builder) {
        SpoutConfig spoutConfig = createSpoutConfig("processedVideo", null);
        builder.setSpout("processedVideoSpout", new KafkaSpout(spoutConfig), 1);
        builder.setBolt("processedVideoBolt", new VideoPreviewBolt(), 1)
                .shuffleGrouping("processedVideoSpout");
    }

    private SpoutConfig createSpoutConfig(String queueName, Scheme scheme) {
        if (scheme == null) {
            scheme = new KafkaJsonEncoder();
        }
        SpoutConfig spoutConfig = new SpoutConfig(
                new KafkaConfig.ZkHosts(getConfiguration().getZookeeperServerNames(), "/kafka/brokers"),
                queueName,
                "/kafka/consumers",
                queueName);
        spoutConfig.scheme = scheme;
        return spoutConfig;
    }
}
