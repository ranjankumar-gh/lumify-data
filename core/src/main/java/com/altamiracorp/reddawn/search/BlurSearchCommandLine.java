package com.altamiracorp.reddawn.search;

import com.altamiracorp.reddawn.cmdline.UcdCommandLineBase;
import com.altamiracorp.reddawn.ucd.models.ArtifactKey;
import org.apache.accumulo.core.util.CachedConfiguration;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.hadoop.util.ToolRunner;

import java.util.Collection;
import java.util.Properties;

public class BlurSearchCommandLine extends UcdCommandLineBase {
    private Integer blurControllerPort = null;
    private String blurControllerLocation = null;
    private String blurHdfsPath = null;
    private String query;

    @Override
    protected Options getOptions() {
        Options options = new Options();

        options.addOption(
                OptionBuilder
                        .withLongOpt(BlurSearchProvider.BLUR_CONTROLLER_PORT)
                        .withDescription("The blur controller port")
                        .withArgName("port")
                        .hasArg()
                        .create()
        );

        options.addOption(
                OptionBuilder
                        .withLongOpt(BlurSearchProvider.BLUR_CONTROLLER_LOCATION)
                        .withDescription("The blur controller location/ip address")
                        .withArgName("address")
                        .hasArg()
                        .create()
        );

        options.addOption(
                OptionBuilder
                        .withLongOpt(BlurSearchProvider.BLUR_PATH)
                        .withDescription("The HDFS path to blur")
                        .withArgName("path")
                        .hasArg()
                        .create()
        );

        options.addOption(
                OptionBuilder
                        .withArgName("q")
                        .withLongOpt("query")
                        .withDescription("The query to run")
                        .withArgName("query")
                        .isRequired()
                        .hasArg()
                        .create()
        );

        return options;
    }

    @Override
    protected void processOptions(CommandLine cmd) {
        if (cmd.hasOption(BlurSearchProvider.BLUR_CONTROLLER_PORT)) {
            blurControllerPort = Integer.parseInt(cmd.getOptionValue(BlurSearchProvider.BLUR_CONTROLLER_PORT));
        }
        if (cmd.hasOption(BlurSearchProvider.BLUR_CONTROLLER_LOCATION)) {
            blurControllerLocation = cmd.getOptionValue(BlurSearchProvider.BLUR_CONTROLLER_LOCATION);
        }
        if (cmd.hasOption(BlurSearchProvider.BLUR_PATH)) {
            blurHdfsPath = cmd.getOptionValue(BlurSearchProvider.BLUR_PATH);
        }
        query = cmd.getOptionValue("query");
    }

    @Override
    protected int run(CommandLine cmd) throws Exception {
        BlurSearchProvider blurSearch = new BlurSearchProvider();
        Properties props = new Properties();
        if (blurControllerPort != null) {
            props.setProperty(BlurSearchProvider.BLUR_CONTROLLER_PORT, "" + blurControllerPort);
        }
        if (blurControllerLocation != null) {
            props.setProperty(BlurSearchProvider.BLUR_CONTROLLER_LOCATION, blurControllerLocation);
        }
        if (blurHdfsPath != null) {
            props.setProperty(BlurSearchProvider.BLUR_PATH, blurHdfsPath);
        }
        blurSearch.setup(props);

        Collection<ArtifactKey> artifactKeys = blurSearch.searchArtifacts(query);
        for (ArtifactKey artifactKey : artifactKeys) {
            System.out.println(artifactKey.toString());
        }
        return 0;
    }

    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(CachedConfiguration.getInstance(), new BlurSearchCommandLine(), args);
        if (res != 0) {
            System.exit(res);
        }
    }
}
