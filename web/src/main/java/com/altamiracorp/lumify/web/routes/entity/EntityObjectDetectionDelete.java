package com.altamiracorp.lumify.web.routes.entity;

import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.lumify.model.Column;
import com.altamiracorp.lumify.model.ModelSession;
import com.altamiracorp.lumify.model.Row;
import com.altamiracorp.lumify.model.graph.GraphRepository;
import com.altamiracorp.lumify.model.termMention.TermMention;
import com.altamiracorp.lumify.model.termMention.TermMentionRowKey;
import com.altamiracorp.lumify.search.SearchProvider;
import com.altamiracorp.lumify.ucd.artifact.Artifact;
import com.altamiracorp.lumify.ucd.artifact.ArtifactRepository;
import com.altamiracorp.lumify.ucd.artifact.ArtifactRowKey;
import com.altamiracorp.lumify.web.BaseRequestHandler;
import com.altamiracorp.web.HandlerChain;
import com.google.inject.Inject;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EntityObjectDetectionDelete extends BaseRequestHandler {
    private final GraphRepository graphRepository;
    private final ArtifactRepository artifactRepository;
    private final SearchProvider searchProvider;
    private final ModelSession modelSession;

    @Inject
    public EntityObjectDetectionDelete(
            final ArtifactRepository artifactRepository,
            final GraphRepository graphRepository,
            final SearchProvider searchProvider,
            final ModelSession modelSession) {
        this.artifactRepository = artifactRepository;
        this.graphRepository = graphRepository;
        this.searchProvider = searchProvider;
        this.modelSession = modelSession;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        JSONObject jsonObject = new JSONObject(getRequiredParameter(request, "objectInfo"));
        User user = getUser(request);

        // Delete from term mention
        TermMentionRowKey termMentionRowKey = new TermMentionRowKey(jsonObject.getString("_rowKey"));
        modelSession.deleteRow(TermMention.TABLE_NAME, termMentionRowKey, user);

        // Delete from titan
        String graphVertexId = jsonObject.getString("graphVertexId");
        JSONObject obj = graphRepository.findVertex(graphVertexId, user).toJson();
        graphRepository.remove(graphVertexId, user);

        // Delete column from Artifact
        Artifact artifact = artifactRepository.findByRowKey(termMentionRowKey.getArtifactRowKey(), user);
        Row<ArtifactRowKey> rowKey = artifactRepository.toRow(artifact);
        String columnFamily = artifact.getArtifactDetectedObjects().getColumnFamilyName();
        String columnQualifier = jsonObject.getJSONObject("info").getString("_rowKey");
        for (Column column : rowKey.get(columnFamily).getColumns()) {
            if (column.getName().equals(columnQualifier)) {
                column.setDirty(true);
            }
        }
        modelSession.deleteColumn(rowKey, Artifact.TABLE_NAME, columnFamily, columnQualifier, user);

        // Overwrite old ElasticSearch index
        Artifact newArtifact = artifactRepository.findByRowKey(termMentionRowKey.getArtifactRowKey(), user);
        searchProvider.add(newArtifact, user);

        respondWithJson(response, obj);
    }
}
