package com.altamiracorp.reddawn.web.routes.entity;

import com.altamiracorp.reddawn.RedDawnSession;
import com.altamiracorp.reddawn.model.graph.GraphRepository;
import com.altamiracorp.reddawn.model.graph.GraphVertex;
import com.altamiracorp.reddawn.model.graph.InMemoryGraphVertex;
import com.altamiracorp.reddawn.model.ontology.LabelName;
import com.altamiracorp.reddawn.model.ontology.PropertyName;
import com.altamiracorp.reddawn.model.ontology.VertexType;
import com.altamiracorp.reddawn.ucd.artifact.Artifact;
import com.altamiracorp.reddawn.ucd.artifact.ArtifactDetectedObjects;
import com.altamiracorp.reddawn.ucd.artifact.ArtifactRepository;
import com.altamiracorp.reddawn.web.Responder;
import com.altamiracorp.reddawn.web.WebApp;
import com.altamiracorp.web.App;
import com.altamiracorp.web.AppAware;
import com.altamiracorp.web.Handler;
import com.altamiracorp.web.HandlerChain;
import com.altamiracorp.web.utils.UrlUtils;
import com.google.common.util.concurrent.MoreExecutors;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class EntityObjectDetectionCreate implements Handler, AppAware {
    private WebApp app;
    private GraphRepository graphRepository = new GraphRepository();
    private ArtifactRepository artifactRepository= new ArtifactRepository();

    private final ExecutorService executorService = MoreExecutors.getExitingExecutorService(
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()),
            0L, TimeUnit.MILLISECONDS);

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        RedDawnSession session = app.getRedDawnSession(request);

        // required parameters
        String artifactRowKey = getRequiredParameter(request, "artifactKey");
        String artifactId = getRequiredParameter(request, "artifactId");
        String sign = getRequiredParameter(request, "sign");
        String conceptId = getRequiredParameter(request, "conceptId");
        String resolvedGraphVertexId = request.getParameter("graphVertexId");
        String boundingBox = getRequiredParameter(request, "coords");
        String detectedObjectRowKey = getRequiredParameter(request, "detectedObjectRowKey");

        GraphVertex conceptVertex = graphRepository.findVertex(session.getGraphSession(), conceptId);
        GraphVertex resolvedVertex;
        if (resolvedGraphVertexId != null) {
            resolvedVertex = graphRepository.findVertex(session.getGraphSession(), resolvedGraphVertexId);
        } else {
            resolvedVertex = graphRepository.findVertexByTitleAndType(session.getGraphSession(), sign, VertexType.ENTITY);
            if (resolvedVertex == null) {
                resolvedVertex = new InMemoryGraphVertex();
                resolvedVertex.setType(VertexType.ENTITY);
            }
            resolvedVertex.setProperty(PropertyName.ROW_KEY, artifactRowKey);
        }

        resolvedVertex.setProperty(PropertyName.BOUNDING_BOX.toString(), boundingBox);
        resolvedVertex.setProperty(PropertyName.SUBTYPE, conceptVertex.getId());
        resolvedVertex.setProperty(PropertyName.TITLE, sign);

        graphRepository.saveVertex(session.getGraphSession(), resolvedVertex);

        graphRepository.saveRelationship(session.getGraphSession(), artifactId, resolvedVertex.getId(), LabelName.CONTAINS_IMAGE_OF);

        Artifact artifact = artifactRepository.findByRowKey(session.getModelSession(), artifactRowKey);
        artifact.getArtifactDetectedObjects().set(detectedObjectRowKey, resolvedVertex.getId());

        artifactRepository.save(session.getModelSession(), artifact);

        JSONObject obj = toJson(resolvedVertex);

        new Responder(response).respondWith(obj);
    }

    @Override
    public void setApp(App app) {
        this.app = (WebApp) app;
    }

    public static String getRequiredParameter(HttpServletRequest request, String parameterName) {
        String parameter = request.getParameter(parameterName);
        if (parameter == null) {
            throw new RuntimeException("'" + parameterName + "' is required.");
        }
        return UrlUtils.urlDecode(parameter);
    }

    private JSONObject toJson(GraphVertex vertex) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("graphVertexId", vertex.getId());
        for (String property : vertex.getPropertyKeys()) {
            obj.put(property, vertex.getProperty(property));
        }
        return obj;
    }
}
