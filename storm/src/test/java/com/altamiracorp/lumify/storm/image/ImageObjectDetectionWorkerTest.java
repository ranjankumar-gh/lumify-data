package com.altamiracorp.lumify.storm.image;

import com.altamiracorp.lumify.core.ingest.AdditionalArtifactWorkData;
import com.altamiracorp.lumify.core.ingest.ArtifactDetectedObject;
import com.altamiracorp.lumify.core.ingest.ArtifactExtractedInfo;
import com.altamiracorp.lumify.objectDetection.OpenCVObjectDetector;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ImageObjectDetectionWorkerTest {
    private ImageObjectDetectionWorker worker;
    @Mock
    com.altamiracorp.lumify.core.user.User user;
    AdditionalArtifactWorkData data;
    FileSystem fs;
    @Mock
    OpenCVObjectDetector detector;
    List<ArtifactDetectedObject> detectedObjects;

    @Before
    public void setup() throws Exception {
        worker = new ImageObjectDetectionWorker();
        data = new AdditionalArtifactWorkData();
        fs = FileSystem.get(new Configuration());
        data.setHdfsFileSystem(fs);
        worker.setObjectDetector(detector);
        detectedObjects = new ArrayList<ArtifactDetectedObject>();
        detectedObjects.add(new ArtifactDetectedObject("", "", "", ""));
    }


    @Test
    public void testDoWork() throws Exception {
        Map stormConf = new HashMap<String, String>();
        stormConf.put("objectdetection.classifierConcepts", "face");
        stormConf.put("objectdetection.classifier.face.path", getClass().getResource("face.xml").getPath());
        worker.prepare(stormConf, user);

        BufferedImage image = ImageIO.read(getClass().getResourceAsStream("test.png"));
        when(detector.detectObjects(image)).thenReturn(detectedObjects);
        ArtifactExtractedInfo result = worker.doWork(image, data);
        assertEquals("[{\"info\":{\"concept\":\"face\",\"coords\":{\"y1\":\"\",\"y2\":\"\",\"x2\":\"\",\"x1\":\"\"}}}]",
                result.getDetectedObjects());

    }

    //todo add test for branch IOException

}
