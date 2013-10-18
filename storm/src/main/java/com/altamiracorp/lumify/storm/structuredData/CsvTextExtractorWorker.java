package com.altamiracorp.lumify.storm.structuredData;

import com.altamiracorp.lumify.FileImporter;
import com.altamiracorp.lumify.core.ingest.AdditionalArtifactWorkData;
import com.altamiracorp.lumify.core.ingest.ArtifactExtractedInfo;
import com.altamiracorp.lumify.core.ingest.structuredData.StructuredDataExtractionWorker;
import com.altamiracorp.lumify.core.model.artifact.Artifact;
import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.lumify.core.util.HdfsLimitOutputStream;
import com.altamiracorp.lumify.core.util.ThreadedTeeInputStreamWorker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.util.List;
import java.util.Map;

public class CsvTextExtractorWorker
        extends ThreadedTeeInputStreamWorker<ArtifactExtractedInfo, AdditionalArtifactWorkData>
        implements StructuredDataExtractionWorker {

    @Override
    protected ArtifactExtractedInfo doWork(InputStream work, AdditionalArtifactWorkData data) throws Exception {
        ArtifactExtractedInfo info = new ArtifactExtractedInfo();

        // Extract mapping json
        JSONObject mappingJson = readMappingJson(data);

        // Extract the csv text
        StringWriter writer = new StringWriter();
        HdfsLimitOutputStream outputStream = new HdfsLimitOutputStream(data.getHdfsFileSystem(), Artifact.MAX_SIZE_OF_INLINE_FILE);
        CsvPreference csvPreference = CsvPreference.EXCEL_PREFERENCE;
        IOUtils.copy(work, outputStream);
        info.setRaw(outputStream.getSmall());
        CsvListReader csvListReader = new CsvListReader(new InputStreamReader(
                new ByteArrayInputStream(outputStream.getSmall())), csvPreference);
        CsvListWriter csvListWriter = new CsvListWriter(writer, csvPreference);
        List<String> line;

        while ((line = csvListReader.read()) != null) {
            csvListWriter.write(line);
        }
        csvListWriter.close();

        info.setText(writer.toString());
        if (mappingJson.has(MappingProperties.SUBJECT)) {
            info.setTitle(mappingJson.get(MappingProperties.SUBJECT).toString());
        }
        info.setMappingJson(mappingJson);

        return info;
    }

    private JSONObject readMappingJson(AdditionalArtifactWorkData data) throws IOException {
        File tempDir = data.getArchiveTempDir();
        for (File f : tempDir.listFiles()) {
            if (f.getName().endsWith(FileImporter.MAPPING_JSON_FILE_NAME_SUFFIX)) {
                return new JSONObject(FileUtils.readFileToString(f));
            }
        }
        throw new RuntimeException("Could not find mapping.json file in directory: " + tempDir);
    }

    @Override
    public String getName() {
        return "csvTextExtractor";
    }

    @Override
    public void prepare(Map stormConf, User user) {
    }
}
