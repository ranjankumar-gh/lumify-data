package io.lumify.storm.video;

import com.google.inject.Inject;
import io.lumify.core.ingest.graphProperty.GraphPropertyWorkData;
import io.lumify.core.ingest.graphProperty.GraphPropertyWorker;
import io.lumify.core.model.properties.MediaLumifyProperties;
import io.lumify.core.model.properties.RawLumifyProperties;
import io.lumify.core.util.ProcessRunner;
import io.lumify.storm.util.*;
import org.json.JSONObject;
import org.securegraph.Element;
import org.securegraph.Property;
import org.securegraph.Vertex;
import org.securegraph.mutation.ExistingElementMutation;
import org.securegraph.property.StreamingPropertyValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VideoPosterFrameWorker extends GraphPropertyWorker {
    private static final String PROPERTY_KEY = VideoPosterFrameWorker.class.getName();
    private ProcessRunner processRunner;

    @Override
    public void execute(InputStream in, GraphPropertyWorkData data) throws Exception {
        File videoPosterFrameFile = File.createTempFile("video_poster_frame", ".png");
        String[] ffmpegOptionsArray = prepareFFMPEGOptionsForPosterFrame(data, videoPosterFrameFile);
        try {
            processRunner.execute(
                    "ffmpeg",
                    ffmpegOptionsArray,
                    null,
                    data.getLocalFile().getAbsolutePath() + ": "
            );

            if (videoPosterFrameFile.length() == 0) {
                throw new RuntimeException("Poster frame not created. Zero length file detected. (from: " + data.getLocalFile().getAbsolutePath() + ")");
            }

            InputStream videoPosterFrameFileIn = new FileInputStream(videoPosterFrameFile);
            try {
                ExistingElementMutation<Vertex> m = data.getElement().prepareMutation();

                StreamingPropertyValue spv = new StreamingPropertyValue(videoPosterFrameFileIn, byte[].class);
                spv.searchIndex(false);
                Map<String, Object> metadata = new HashMap<String, Object>();
                metadata.put(RawLumifyProperties.MIME_TYPE.getPropertyName(), "image/png");
                MediaLumifyProperties.RAW_POSTER_FRAME.addPropertyValue(m, PROPERTY_KEY, spv, metadata, data.getProperty().getVisibility());
                m.save(getAuthorizations());
                getGraph().flush();
            } finally {
                videoPosterFrameFileIn.close();
            }
        } finally {
            videoPosterFrameFile.delete();
        }
    }

    private String[] prepareFFMPEGOptionsForPosterFrame(GraphPropertyWorkData data, File videoPosterFrameFile) {
        JSONObject json = JSONExtractor.retrieveJSONObjectUsingFFPROBE(processRunner, data);

        Integer videoWidth = VideoDimensionsUtil.extractWidthFromJSON(json);
        Integer videoHeight = VideoDimensionsUtil.extractHeightFromJSON(json);
        Integer videoRotation = VideoRotationUtil.extractRotationFromJSON(json);
        if (videoRotation == null)
            videoRotation = 0;
        int[] displayDimensions = VideoDimensionsUtil.calculateDisplayDimensions(videoWidth, videoHeight, videoRotation);

        Double duration = DurationUtil.extractDurationFromJSON(json);
        String durationString = null;
        if (duration != null) {
            durationString = Integer.toString((int) (duration / 4));
        }

        ArrayList<String> ffmpegOptionsList = new ArrayList<String>();
        //Add the time offset for where the poster frame will be taken.
        if (durationString != null) {
            ffmpegOptionsList.add("-itsoffset");
            ffmpegOptionsList.add("-" + durationString);
        }

        ffmpegOptionsList.add("-i");
        ffmpegOptionsList.add(data.getLocalFile().getAbsolutePath());
        ffmpegOptionsList.add("-vcodec");
        ffmpegOptionsList.add("png");
        ffmpegOptionsList.add("-vframes");
        ffmpegOptionsList.add("1");
        ffmpegOptionsList.add("-an");
        ffmpegOptionsList.add("-f");
        ffmpegOptionsList.add("rawvideo");

        //Scale.
        ffmpegOptionsList.add("-s");
        ffmpegOptionsList.add(displayDimensions[0] + "x" + displayDimensions[1]);

        //Rotation.
        String[] ffmpegRotationOptions = VideoRotationUtil.createFFMPEGRotationOptions(videoRotation);
        if (ffmpegRotationOptions != null) {
            ffmpegOptionsList.add(ffmpegRotationOptions[0]);
            ffmpegOptionsList.add(ffmpegRotationOptions[1]);
        }

        ffmpegOptionsList.add("-y");
        ffmpegOptionsList.add(videoPosterFrameFile.getAbsolutePath());
        String[] ffmpegOptionsArray = StringUtil.createStringArrayFromList(ffmpegOptionsList);
        return ffmpegOptionsArray;
    }

    @Override
    public boolean isHandled(Element element, Property property) {
        if (property == null) {
            return false;
        }

        if (!property.getName().equals(RawLumifyProperties.RAW.getPropertyName())) {
            return false;
        }
        String mimeType = (String) property.getMetadata().get(RawLumifyProperties.MIME_TYPE.getPropertyName());
        if (mimeType == null || !mimeType.startsWith("video")) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isLocalFileRequired() {
        return true;
    }

    @Inject
    public void setProcessRunner(ProcessRunner ffmpeg) {
        this.processRunner = ffmpeg;
    }
}
