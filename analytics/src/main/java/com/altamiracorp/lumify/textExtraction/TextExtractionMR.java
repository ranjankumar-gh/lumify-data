package com.altamiracorp.lumify.textExtraction;

import com.altamiracorp.lumify.ConfigurableMapJobBase;
import com.altamiracorp.lumify.LumifyMapper;
import com.altamiracorp.lumify.config.Configuration;
import com.altamiracorp.lumify.model.AccumuloModelOutputFormat;
import com.altamiracorp.lumify.ucd.AccumuloArtifactInputFormat;
import com.altamiracorp.lumify.ucd.artifact.Artifact;
import com.altamiracorp.lumify.ucd.artifact.ArtifactRepository;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.accumulo.core.util.CachedConfiguration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextExtractionMR extends ConfigurableMapJobBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextExtractionMR.class.getName());

    @Override
    protected Class getMapperClass(Job job, Class clazz) {
        TextExtractorMapper.init(job, clazz);
        return TextExtractorMapper.class;
    }

    @Override
    protected Class<? extends InputFormat> getInputFormatClassAndInit(Job job) {
        Configuration c = getConfiguration();
        AccumuloArtifactInputFormat.init(job, c.getDataStoreUserName(), c.getDataStorePassword(), getAuthorizations(), c.getZookeeperInstanceName(), c.getZookeeperServerNames());
        return AccumuloArtifactInputFormat.class;
    }

    @Override
    protected Class<? extends OutputFormat> getOutputFormatClass() {
        return AccumuloModelOutputFormat.class;
    }

    public static class TextExtractorMapper extends LumifyMapper<Text, Artifact, Text, Artifact> {
        public static final String CONF_TEXT_EXTRACTOR_CLASS = "textExtractorClass";
        public static final Text ARTIFACT_TABLE_NAME = new Text(Artifact.TABLE_NAME);
        private TextExtractor textExtractor;
        private ArtifactRepository artifactRepository;

        @Override
        protected void setup(Context context, Injector injector) throws Exception {
            textExtractor = getAndInjectClassFromConfiguration(context, injector, CONF_TEXT_EXTRACTOR_CLASS);
            textExtractor.setup(context, injector);
        }

        @Override
        public void safeMap(Text rowKey, Artifact artifact, Context context) throws Exception {
            LOGGER.info("Extracting text from artifact: " + artifact.getRowKey().toString());
            ArtifactExtractedInfo extractedInfo = textExtractor.extract(artifact, getUser());
            if (extractedInfo == null) {
                return;
            }

            if (extractedInfo.getText() != null) {
                artifact.getArtifactExtractedText().addExtractedText(textExtractor.getName(), cleanExtractedText(extractedInfo.getText()));
            }

            if (extractedInfo.getSubject() != null && !extractedInfo.getSubject().equals("")) {
                artifact.getGenericMetadata().setSubject(extractedInfo.getSubject());
                artifactRepository.saveToGraph(artifact, getUser());
            }

            if (extractedInfo.getDate() != null) {
                artifact.getGenericMetadata().setDocumentDtg(extractedInfo.getDate());
            }

            if (extractedInfo.getType() != null) {
                artifact.getGenericMetadata().setDocumentType(extractedInfo.getType());
            }

            if (extractedInfo.getUrl() != null && !extractedInfo.getUrl().equals("")) {
                artifact.getGenericMetadata().setSource(extractedInfo.getUrl());
            }

            if (extractedInfo.getExtUrl() != null) {
                artifact.getGenericMetadata().setExternalUrl(extractedInfo.getExtUrl());
            }

            if (extractedInfo.getSrcType() != null) {
                artifact.getGenericMetadata().setSourceType(extractedInfo.getSrcType());
            }

            if (extractedInfo.getDate() != null) {
                artifact.getGenericMetadata().setFileTimestamp(extractedInfo.getDate().getTime() / 1000);
            }

            if (extractedInfo.getRetrievalTime() != null) {
                artifact.getGenericMetadata().setLoadTimestamp(extractedInfo.getRetrievalTime());
            }

            context.write(ARTIFACT_TABLE_NAME, artifact);
        }

        private String cleanExtractedText(String extractedText) {
            StringBuilder trimmedText = new StringBuilder();
            String[] splitResults = extractedText.split("\\n");
            for (int i = 0; i < splitResults.length; i++) {
                trimmedText.append(splitResults[i].trim());
                if (i != splitResults.length) {
                    trimmedText.append("\n");
                }
            }

            return trimmedText.toString().replaceAll("\\n{3,}", "\n\n");
        }

        public static void init(Job job, Class<? extends TextExtractor> textExtractorClass) {
            job.getConfiguration().setClass(CONF_TEXT_EXTRACTOR_CLASS, textExtractorClass, TextExtractor.class);
        }

        @Inject
        public void setArtifactRepository(ArtifactRepository artifactRepository) {
            this.artifactRepository = artifactRepository;
        }
    }

    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(CachedConfiguration.getInstance(), new TextExtractionMR(), args);
        if (res != 0) {
            System.exit(res);
        }
    }
}

