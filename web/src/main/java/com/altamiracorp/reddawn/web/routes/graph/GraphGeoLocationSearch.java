package com.altamiracorp.reddawn.web.routes.graph;

import com.altamiracorp.reddawn.RedDawnSession;
import com.altamiracorp.reddawn.model.graph.GraphNode;
import com.altamiracorp.reddawn.model.graph.GraphRepository;
import com.altamiracorp.reddawn.web.Responder;
import com.altamiracorp.reddawn.web.WebApp;
import com.altamiracorp.web.App;
import com.altamiracorp.web.AppAware;
import com.altamiracorp.web.Handler;
import com.altamiracorp.web.HandlerChain;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class GraphGeoLocationSearch implements Handler, AppAware {
    private GraphRepository graphRepository = new GraphRepository();
    private WebApp app;

    @Override
    public void setApp(App app) {
        this.app = (WebApp) app;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        RedDawnSession session = app.getRedDawnSession(request);
        double latitude = Double.parseDouble(request.getParameter("lat"));
        double longitude = Double.parseDouble(request.getParameter("lon"));
        double radius = Double.parseDouble(request.getParameter("radius"));

        List<GraphNode> nodes = graphRepository.findByGeoLocation(session.getGraphSession(), latitude, longitude, radius);

        JSONObject results = new JSONObject();
        results.put("nodes", GraphNode.toJson(nodes));
        new Responder(response).respondWith(results);
    }
}
