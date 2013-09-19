package com.altamiracorp.lumify.textExtraction;

import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.lumify.model.videoFrames.VideoFrame;
import com.altamiracorp.lumify.structuredDataExtraction.StructuredDataExtractorBase;
import com.altamiracorp.lumify.structuredDataExtraction.StructuredDataFactory;
import com.altamiracorp.lumify.ucd.artifact.Artifact;
import org.apache.hadoop.mapreduce.Mapper;
import org.json.JSONObject;

public class StructuredDataTextExtractor implements TextExtractor {
    public static final String NAME = "structuredData";
    private StructuredDataFactory structedDataFactory;

    @Override
    public void setup(Mapper.Context context) throws Exception {
        structedDataFactory = new StructuredDataFactory(context);
    }

    @Override
    public ArtifactExtractedInfo extract(Artifact artifact, User user) throws Exception {
        JSONObject mappingJson = artifact.getGenericMetadata().getMappingJson();
        if (mappingJson == null) {
            return null;
        }
        String type = mappingJson.getString("type");
        StructuredDataExtractorBase extractor = structedDataFactory.get(type);
        return extractor.extractText(artifact, user);
    }

    @Override
    public VideoFrameExtractedInfo extract(VideoFrame videoFrame, User user) throws Exception {
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
