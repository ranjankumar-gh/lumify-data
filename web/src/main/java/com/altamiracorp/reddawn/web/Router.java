package com.altamiracorp.reddawn.web;

import com.altamiracorp.reddawn.web.routes.admin.AdminQuery;
import com.altamiracorp.reddawn.web.routes.admin.AdminTables;
import com.altamiracorp.reddawn.web.routes.artifact.*;
import com.altamiracorp.reddawn.web.routes.chat.ChatNew;
import com.altamiracorp.reddawn.web.routes.chat.ChatPostMessage;
import com.altamiracorp.reddawn.web.routes.concept.ConceptList;
import com.altamiracorp.reddawn.web.routes.entity.*;
import com.altamiracorp.reddawn.web.routes.graph.*;
import com.altamiracorp.reddawn.web.routes.map.MapInitHandler;
import com.altamiracorp.reddawn.web.routes.map.MapTileHandler;
import com.altamiracorp.reddawn.web.routes.node.NodeProperties;
import com.altamiracorp.reddawn.web.routes.node.NodeRelationships;
import com.altamiracorp.reddawn.web.routes.node.NodeToNodeRelationship;
import com.altamiracorp.reddawn.web.routes.predicate.PredicateList;
import com.altamiracorp.reddawn.web.routes.statement.StatementByRowKey;
import com.altamiracorp.reddawn.web.routes.statement.StatementCreate;
import com.altamiracorp.reddawn.web.routes.user.MeGet;
import com.altamiracorp.reddawn.web.routes.user.MessagesGet;
import com.altamiracorp.reddawn.web.routes.workspace.WorkspaceByRowKey;
import com.altamiracorp.reddawn.web.routes.workspace.WorkspaceDelete;
import com.altamiracorp.reddawn.web.routes.workspace.WorkspaceList;
import com.altamiracorp.reddawn.web.routes.workspace.WorkspaceSave;
import com.altamiracorp.web.Handler;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class Router extends HttpServlet {
    private WebApp app;
    final File rootDir = new File("./web/src/main/webapp");

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        app = new WebApp(config);

        Class<? extends Handler> authenticator = X509Authenticator.class;
        if (app.get("env").equals("dev")) {
            authenticator = DevBasicAuthenticator.class;
        }

        app.get("/concept/", ConceptList.class);

        app.get("/predicate/", PredicateList.class);

        app.get("/artifact/search", authenticator, ArtifactSearch.class);
        app.get("/artifact/{rowKey}/terms", authenticator, ArtifactTermsByRowKey.class);
        app.get("/artifact/{rowKey}/text", authenticator, ArtifactTextByRowKey.class);
        app.get("/artifact/{rowKey}/raw", authenticator, ArtifactRawByRowKey.class);
        app.get("/artifact/{rowKey}/poster-frame", authenticator, ArtifactPosterFrameByRowKey.class);
        app.get("/artifact/{rowKey}/video-preview", authenticator, ArtifactVideoPreviewImageByRowKey.class);
        app.get("/artifact/{rowKey}", authenticator, ArtifactByRowKey.class);

        app.get("/statement/{rowKey}", authenticator, StatementByRowKey.class);
        app.post("/statement/create", authenticator, StatementCreate.class);

        app.post("/entity/relationships", authenticator, EntityRelationships.class);
        app.get("/entity/{rowKey}", authenticator, EntityByRowKey.class);
        app.post("/entity/create", authenticator, EntityCreate.class);

        app.get("/node/{graphNodeId}/properties", authenticator, NodeProperties.class);
        app.get("/node/{graphNodeId}/relationships", authenticator, NodeRelationships.class);
        app.get("/node/relationship", authenticator, NodeToNodeRelationship.class);

        app.get("/graph/{graphNodeId}/relatedNodes", authenticator, GraphRelatedNodes.class);
        app.get("/graph/{graphNodeId}/relatedResolvedNodes", authenticator, GraphRelatedResolvedNodes.class);
        app.get("/graph/node/search", authenticator, GraphNodeSearch.class);
        app.get("/graph/node/geoLocationSearch", authenticator, GraphGeoLocationSearch.class);
        app.get("/graph/node/{graphNodeId}", authenticator, GraphGetNode.class);

        app.get("/workspace/", authenticator, WorkspaceList.class);
        app.post("/workspace/save", authenticator, WorkspaceSave.class);
        app.post("/workspace/{workspaceRowKey}/save", authenticator, WorkspaceSave.class);
        app.get("/workspace/{workspaceRowKey}", authenticator, WorkspaceByRowKey.class);
        app.delete("/workspace/{workspaceRowKey}", authenticator, WorkspaceDelete.class);

        app.get("/user/messages", authenticator, MessagesGet.class);
        app.get("/user/me", authenticator, MeGet.class);

        app.get("/map/map-init.js", MapInitHandler.class);
        app.get("/map/{z}/{x}/{y}.png", MapTileHandler.class);

        app.post("/chat/new", authenticator, ChatNew.class);
        app.post("/chat/{chatId}/post", authenticator, ChatPostMessage.class);

        app.get("/admin/query", authenticator, AdminQuery.class);
        app.get("/admin/tables", authenticator, AdminTables.class);

        LessRestlet.init(rootDir);
        app.get("/css/{file}.css", LessRestlet.class);
    }

    @Override
    public void service(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
        try {
            HttpServletResponse httpResponse = (HttpServletResponse) resp;
            httpResponse.addHeader("Accept-Ranges", "bytes");
            app.handle((HttpServletRequest) req, httpResponse);
            app.close(req);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
