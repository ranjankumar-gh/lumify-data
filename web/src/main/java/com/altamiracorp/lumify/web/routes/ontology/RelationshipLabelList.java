package com.altamiracorp.lumify.web.routes.ontology;

import com.altamiracorp.lumify.AppSession;
import com.altamiracorp.lumify.model.ontology.OntologyRepository;
import com.altamiracorp.lumify.model.ontology.Relationship;
import com.altamiracorp.lumify.web.BaseRequestHandler;
import com.altamiracorp.web.HandlerChain;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class RelationshipLabelList extends BaseRequestHandler {
    private OntologyRepository ontologyRepository = new OntologyRepository();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        AppSession session = app.getAppSession(request);

        List<Relationship> relationships = ontologyRepository.getRelationshipLabels(session.getGraphSession());

        JSONObject json = new JSONObject();
        json.put("relationships", Relationship.toJsonRelationships(relationships));

        respondWithJson(response, json);
    }
}
