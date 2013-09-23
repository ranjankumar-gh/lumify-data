package com.altamiracorp.lumify.web.routes.graph;

import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.lumify.model.graph.GraphRepository;
import com.altamiracorp.lumify.model.graph.GraphVertex;
import com.altamiracorp.lumify.model.ontology.OntologyRepository;
import com.altamiracorp.lumify.model.ontology.Property;
import com.altamiracorp.lumify.web.BaseRequestHandler;
import com.altamiracorp.web.HandlerChain;
import com.google.inject.Inject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class GraphVertexSearch extends BaseRequestHandler {
    private final GraphRepository graphRepository;
    private final OntologyRepository ontologyRepository;

    @Inject
    public GraphVertexSearch(final OntologyRepository ontologyRepo, final GraphRepository graphRepo) {
        ontologyRepository = ontologyRepo;
        graphRepository = graphRepo;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        final String query = getRequiredParameter(request, "q");
        final String filter = getRequiredParameter(request, "filter");

        User user = getUser(request);
        JSONArray filterJson = new JSONArray(filter);

        ontologyRepository.resolvePropertyIds(filterJson, user);

        graphRepository.commit();
        List<GraphVertex> vertices = graphRepository.searchVerticesByTitle(query, filterJson, user);
        JSONObject results = new JSONObject();
        results.put("vertices", GraphVertex.toJson(vertices));

        respondWithJson(response, results);
    }
}
