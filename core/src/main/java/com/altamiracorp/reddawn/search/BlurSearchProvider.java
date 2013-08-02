package com.altamiracorp.reddawn.search;

import com.altamiracorp.reddawn.ucd.artifact.Artifact;
import com.altamiracorp.reddawn.ucd.artifact.ArtifactType;
import com.altamiracorp.reddawn.ucd.term.Term;
import org.apache.blur.thirdparty.thrift_0_9_0.TException;
import org.apache.blur.thrift.BlurClient;
import org.apache.blur.thrift.generated.*;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public class BlurSearchProvider implements SearchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlurSearchProvider.class.getName());

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    public static final String BLUR_CONTROLLER_LOCATION = "blurControllerLocation";
    public static final String BLUR_PATH = "blurPath";
    private static final String ARTIFACT_BLUR_TABLE_NAME = "artifact";
    private static final String GENERIC_COLUMN_FAMILY_NAME = "generic";
    private static final String TEXT_COLUMN_NAME = "text";
    private static final String SUBJECT_COLUMN_NAME = "subject";
    private static final String PUBLISHED_DATE_COLUMN_NAME = "publishedDate";
    private static final String GRAPH_NODE_ID_COLUMN_NAME = "graphNodeId";
    private static final String SOURCE_COLUMN_NAME = "source";
    private static final String ARTIFACT_TYPE = "type";
    private static final String TERM_BLUR_TABLE_NAME = "term";
    private static final String SHA_COLUMN_FAMILY_NAME = "sha";
    private static final String SIGN_COLUMN_NAME = "sign";
    private static final String CONCEPT_LABEL_COLUMN_NAME = "conceptLabel";

    private Blur.Iface client;
    private String blurPath;

    @Override
    public void setup(Mapper.Context context) throws Exception {
        String blurControllerLocation = context.getConfiguration().get(BLUR_CONTROLLER_LOCATION, "192.168.33.10:40010");
        String blurPath = context.getConfiguration().get(BLUR_PATH, "192.168.33.10:40010");

        init(blurControllerLocation, blurPath);
    }

    public void setup(Properties props) {
        String blurControllerLocation = props.getProperty(BLUR_CONTROLLER_LOCATION, "192.168.33.10:40010");
        String blurPath = props.getProperty(BLUR_PATH, "hdfs://192.168.33.10/blur");

        try {
            init(blurControllerLocation, blurPath);
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    private void init(String blurControllerLocation, String blurPath) throws TException {
        LOGGER.info("Connecting to blur: blurControllerLocation=" + blurControllerLocation + ", blurPath=" + blurPath);

        this.client = BlurClient.getClient(blurControllerLocation);

        this.blurPath = blurPath;
        this.initializeTables();
    }

    @Override
    public void initializeTables() {
        LOGGER.info("Creating blur tables");
        AnalyzerDefinition ad = new AnalyzerDefinition();
        try {
            List<String> tableList = this.client.tableList();
            String[] blurTables = new String[]{TERM_BLUR_TABLE_NAME, ARTIFACT_BLUR_TABLE_NAME};

            for (String blurTable : blurTables) {
                if (!tableList.contains(blurTable)) {
                    LOGGER.info("Creating table: " + blurTable);
                    createTable(client, blurPath, ad, blurTable);
                } else {
                    LOGGER.info("Skipping create table '" + blurTable + "' already exists.");
                }
            }
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    private void createTable(Blur.Iface client, String blurPath, AnalyzerDefinition ad, String tableName) throws TException {
        LOGGER.info("Creating blur table: " + tableName);

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
        LOGGER.info("Adding artifact \"" + artifact.getRowKey().toString() + "\" to full text search.");
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

        String text = artifact.getContent().getDocExtractedTextString();
        String subject = artifact.getGenericMetadata().getSubject();
        String id = artifact.getRowKey().toString();
        String publishedDate = dateFormat.format(artifact.getPublishedDate());
        String source = artifact.getGenericMetadata().getSource();
        String graphNodeId = artifact.getGenericMetadata().getGraphNodeId();

        if (text == null) {
            text = "";
        }
        if (subject == null) {
            subject = "";
        }

        List<Column> columns = new ArrayList<Column>();
        columns.add(new Column(TEXT_COLUMN_NAME, text));
        columns.add(new Column(SUBJECT_COLUMN_NAME, subject));
        columns.add(new Column(PUBLISHED_DATE_COLUMN_NAME, publishedDate));
        columns.add(new Column(ARTIFACT_TYPE, artifact.getType().toString()));
        if (graphNodeId != null) {
            columns.add(new Column(GRAPH_NODE_ID_COLUMN_NAME, graphNodeId));
        }
        if (source != null) {
            columns.add(new Column(SOURCE_COLUMN_NAME, source));
        }

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
    public Collection<ArtifactSearchResult> searchArtifacts(String query) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        BlurQuery blurQuery = new BlurQuery();
        SimpleQuery simpleQuery = new SimpleQuery();
        simpleQuery.setQueryStr(query);
        blurQuery.setSimpleQuery(simpleQuery);
        blurQuery.setSelector(new Selector());

        BlurResults blurResults = client.query(ARTIFACT_BLUR_TABLE_NAME, blurQuery);
        ArrayList<ArtifactSearchResult> results = new ArrayList<ArtifactSearchResult>();
        for (BlurResult blurResult : blurResults.getResults()) {
            Row row = blurResult.getFetchResult().getRowResult().getRow();
            String rowId = row.getId();
            String subject = "";
            assert row.getRecordCount() == 1;
            Record record = row.getRecords().get(0);
            assert record.getFamily().equals(GENERIC_COLUMN_FAMILY_NAME);
            Date publishedDate = null;
            String source = null;
            String graphNodeId = null;
            ArtifactType artifactType = ArtifactType.DOCUMENT;

            for (Column column : record.getColumns()) {
                if (column.getName().equals(SUBJECT_COLUMN_NAME)) {
                    subject = column.getValue();
                } else if (column.getName().equals(PUBLISHED_DATE_COLUMN_NAME)) {
                    publishedDate = dateFormat.parse(column.getValue());
                } else if (column.getName().equals(SOURCE_COLUMN_NAME)) {
                    source = column.getValue();
                } else if (column.getName().equals(ARTIFACT_TYPE)) {
                    artifactType = ArtifactType.valueOf(column.getValue());
                } else if (column.getName().equals(GRAPH_NODE_ID_COLUMN_NAME)) {
                    graphNodeId = column.getValue();
                }
            }

            ArtifactSearchResult result = new ArtifactSearchResult(rowId, subject, publishedDate, source, artifactType, graphNodeId);
            results.add(result);
        }
        return results;
    }

    @Override
    public void deleteTables() {
        deleteTable(ARTIFACT_BLUR_TABLE_NAME);
        deleteTable(TERM_BLUR_TABLE_NAME);
    }

    private void deleteTable(String tableName) {
        try {
            LOGGER.info("Deleting blur table: " + tableName);
            client.disableTable(tableName);
            client.removeTable(tableName, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void add(Term term) throws Exception {
        if (term.getRowKey().toString() == null) {
            return;
        }

        LOGGER.info("Adding term \"" + term.getRowKey().toString() + "\" for partial term search");
        String id = term.getRowKey().toString();
        String sign = term.getRowKey().getSign();
        String conceptLabel = term.getRowKey().getConceptLabel();
        if (sign == null) {
            sign = "";
        }

        List<Column> columns = new ArrayList<Column>();
        columns.add(new Column(SIGN_COLUMN_NAME, sign));
        columns.add(new Column(CONCEPT_LABEL_COLUMN_NAME, conceptLabel));

        Record record = new Record();
        record.setRecordId(id);
        record.setFamily(SHA_COLUMN_FAMILY_NAME);
        record.setColumns(columns);

        RecordMutation recordMutation = new RecordMutation();
        recordMutation.setRecord(record);
        recordMutation.setRecordMutationType(RecordMutationType.REPLACE_ENTIRE_RECORD);

        List<RecordMutation> recordMutations = new ArrayList<RecordMutation>();
        recordMutations.add(recordMutation);

        RowMutation mutation = new RowMutation();
        mutation.setTable(TERM_BLUR_TABLE_NAME);
        mutation.setRowId(id);
        mutation.setRowMutationType(RowMutationType.REPLACE_ROW);
        mutation.setRecordMutations(recordMutations);

        client.mutate(mutation);
    }
}
