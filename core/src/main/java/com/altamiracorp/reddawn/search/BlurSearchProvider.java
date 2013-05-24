package com.altamiracorp.reddawn.search;

import com.altamiracorp.reddawn.ucd.models.Artifact;
import com.altamiracorp.reddawn.ucd.models.ArtifactKey;
import org.apache.blur.thirdparty.thrift_0_9_0.TException;
import org.apache.blur.thirdparty.thrift_0_9_0.protocol.TBinaryProtocol;
import org.apache.blur.thirdparty.thrift_0_9_0.protocol.TProtocol;
import org.apache.blur.thirdparty.thrift_0_9_0.transport.TFramedTransport;
import org.apache.blur.thirdparty.thrift_0_9_0.transport.TSocket;
import org.apache.blur.thirdparty.thrift_0_9_0.transport.TTransport;
import org.apache.blur.thrift.generated.*;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public class BlurSearchProvider implements SearchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlurSearchProvider.class.getName());

    public static final String BLUR_CONTROLLER_PORT = "blurControllerPort";
    public static final String BLUR_CONTROLLER_LOCATION = "blurControllerLocation";
    public static final String BLUR_PATH = "blurPath";
    private static final String ARTIFACT_BLUR_TABLE_NAME = "artifact";
    private static final String GENERIC_COLUMN_FAMILY_NAME = "generic";
    private static final String TEXT_COLUMN_NAME = "text";
    private Blur.Client client;

    @Override
    public void setup(Mapper.Context context) throws Exception {
        String blurControllerLocation = context.getConfiguration().get(BLUR_CONTROLLER_LOCATION, "192.168.33.10");
        int blurControllerPort = context.getConfiguration().getInt(BLUR_CONTROLLER_PORT, 40010);
        String blurPath = context.getConfiguration().get(BLUR_PATH, "hdfs://192.168.33.10/blur");

        init(blurControllerLocation, blurControllerPort, blurPath);
    }

    public void setup(Properties props) throws Exception {
        String blurControllerLocation = props.getProperty(BLUR_CONTROLLER_LOCATION, "192.168.33.10");
        int blurControllerPort = Integer.parseInt(props.getProperty(BLUR_CONTROLLER_PORT, "40010"));
        String blurPath = props.getProperty(BLUR_PATH, "hdfs://192.168.33.10/blur");

        init(blurControllerLocation, blurControllerPort, blurPath);
    }

    private void init(String blurControllerLocation, int blurControllerPort, String blurPath) throws TException {
        TTransport trans = new TSocket(blurControllerLocation, blurControllerPort);
        trans.open();
        TProtocol proto = new TBinaryProtocol(new TFramedTransport(trans));
        this.client = new Blur.Client.Factory().getClient(proto);

        createTables(blurPath);
    }

    private void createTables(String blurPath) throws TException {
        AnalyzerDefinition ad = new AnalyzerDefinition();
        List<String> tableList = this.client.tableList();

        if (!tableList.contains(ARTIFACT_BLUR_TABLE_NAME)) {
            createTable(client, blurPath, ad, ARTIFACT_BLUR_TABLE_NAME);
        }
    }

    private void createTable(Blur.Client client, String blurPath, AnalyzerDefinition ad, String tableName) throws TException {
        TableDescriptor td = new TableDescriptor();
        td.setShardCount(16);
        td.setTableUri(blurPath + "/tables/" + tableName);
        td.setAnalyzerDefinition(ad);
        td.setName(tableName);

        LOGGER.info("Creating table: " + tableName);
        client.createTable(td);
    }

    @Override
    public void add(Artifact artifact) throws Exception {
        if (artifact.getContent() == null) {
            return;
        }
        LOGGER.info("Adding artifact \"" + artifact.getKey().toString() + "\" to full text search.");
        String text = artifact.getContent().getDocExtractedText();
        String id = artifact.getKey().toString();

        List<Column> columns = new ArrayList<Column>();
        columns.add(new Column(TEXT_COLUMN_NAME, text));

        Record record = new Record();
        record.setRecordId(id);
        record.setFamily(GENERIC_COLUMN_FAMILY_NAME);
        record.setColumns(columns);

        RecordMutation recordMutation = new RecordMutation();
        recordMutation.setRecord(record);
        recordMutation.setRecordMutationType(RecordMutationType.REPLACE_ENTIRE_RECORD);

        List<RecordMutation> recordMutations = new ArrayList<RecordMutation>();
        recordMutations.add(recordMutation);

        RowMutation mutation = new RowMutation();
        mutation.setTable(ARTIFACT_BLUR_TABLE_NAME);
        mutation.setRowId(id);
        mutation.setRowMutationType(RowMutationType.REPLACE_ROW);
        mutation.setRecordMutations(recordMutations);

        client.mutate(mutation);
    }

    @Override
    public Collection<ArtifactKey> searchArtifacts(String query) throws Exception {
        BlurQuery blurQuery = new BlurQuery();
        SimpleQuery simpleQuery = new SimpleQuery();
        simpleQuery.setQueryStr(query);
        blurQuery.setSimpleQuery(simpleQuery);
        blurQuery.setSelector(new Selector());

        BlurResults blurResults = client.query(ARTIFACT_BLUR_TABLE_NAME, blurQuery);
        ArrayList<ArtifactKey> results = new ArrayList<ArtifactKey>();
        for (BlurResult blurResult : blurResults.getResults()) {
            String rowId = blurResult.getFetchResult().getRowResult().getRow().getId();
            ArtifactKey artifactKey = new ArtifactKey(rowId);
            results.add(artifactKey);
        }
        return results;
    }
}
