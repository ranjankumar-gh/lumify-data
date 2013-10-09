package com.altamiracorp.lumify.storm.termExtraction;

import com.altamiracorp.lumify.config.ConfigurationHelper;
import com.altamiracorp.lumify.core.ingest.termExtraction.TermExtractionAdditionalWorkData;
import com.altamiracorp.lumify.core.ingest.termExtraction.TermExtractionWorker;
import com.altamiracorp.lumify.core.ingest.termExtraction.TermExtractionResult;
import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.lumify.entityExtraction.KnownEntityExtractor;
import com.google.inject.Inject;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class KnownEntityExtractorWorker extends TermExtractionWorker {
    private KnownEntityExtractor knownEntityExtractor;

    @Override
    protected TermExtractionResult doWork(InputStream work, TermExtractionAdditionalWorkData termExtractionAdditionalWorkData) throws Exception {
        return knownEntityExtractor.extract(work);
    }

    public void prepare(Map conf, User user) throws IOException {
        Configuration configuration = ConfigurationHelper.createHadoopConfigurationFromMap(conf);
        knownEntityExtractor.prepare(configuration, user);
    }

    @Inject
    void setKnownEntityExtractor(KnownEntityExtractor knownEntityExtractor) {
        this.knownEntityExtractor = knownEntityExtractor;
    }
}
