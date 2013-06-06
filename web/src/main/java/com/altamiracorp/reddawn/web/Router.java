package com.altamiracorp.reddawn.web;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.altamiracorp.reddawn.web.routes.artifact.ArtifactByRowKey;
import com.altamiracorp.reddawn.web.routes.artifact.ArtifactHtmlByRowKey;
import com.altamiracorp.reddawn.web.routes.artifact.ArtifactRawByRowKey;
import com.altamiracorp.reddawn.web.routes.artifact.ArtifactSearch;
import com.altamiracorp.reddawn.web.routes.artifact.ArtifactTermsByRowKey;
import com.altamiracorp.reddawn.web.routes.artifact.ArtifactTextByRowKey;
import com.altamiracorp.reddawn.web.routes.chat.ChatNew;
import com.altamiracorp.reddawn.web.routes.chat.ChatPostMessage;
import com.altamiracorp.reddawn.web.routes.entity.EntityByRowKey;
import com.altamiracorp.reddawn.web.routes.entity.EntityRelationships;
import com.altamiracorp.reddawn.web.routes.entity.EntitySearch;
import com.altamiracorp.reddawn.web.routes.user.MeGet;
import com.altamiracorp.reddawn.web.routes.user.MessagesGet;
import com.altamiracorp.reddawn.web.routes.workspace.WorkspaceByRowKey;
import com.altamiracorp.reddawn.web.routes.workspace.WorkspaceList;
import com.altamiracorp.reddawn.web.routes.workspace.WorkspaceSave;
import com.altamiracorp.web.App;
import com.altamiracorp.web.Handler;

public class Router extends HttpServlet {
    private App app;
    final File rootDir = new File("./web/src/main/webapp");

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        app = new WebApp(config);

        Class<? extends Handler> authenticator = X509Authenticator.class;
        if (app.get("env").equals("dev")) {
            authenticator = DevBasicAuthenticator.class;
        }

        app.get("/artifact/search", authenticator, ArtifactSearch.class);
        app.get("/artifact/{rowKey}/terms", authenticator, ArtifactTermsByRowKey.class);
        app.get("/artifact/{rowKey}/text", authenticator, ArtifactTextByRowKey.class);
        app.get("/artifact/{rowKey}/html", authenticator, ArtifactHtmlByRowKey.class);
        app.get("/artifact/{rowKey}/raw", authenticator, ArtifactRawByRowKey.class);
        app.get("/artifact/{rowKey}", authenticator, ArtifactByRowKey.class);

        app.get("/entity/relationships", authenticator, EntityRelationships.class);
        app.get("/entity/search", authenticator, EntitySearch.class);
        app.get("/entity/{rowKey}", authenticator, EntityByRowKey.class);

        app.get("/workspace/", authenticator, WorkspaceList.class);
        app.post("/workspace/save", authenticator, WorkspaceSave.class);
        app.post("/workspace/{workspaceRowKey}/save", authenticator, WorkspaceSave.class);
        app.get("/workspace/{workspaceRowKey}", authenticator, WorkspaceByRowKey.class);

        app.get("/user/messages", authenticator, MessagesGet.class);
		app.get("/user/me", authenticator, MeGet.class);

        app.post("/chat/new", authenticator, ChatNew.class);
        app.post("/chat/{chatId}/post", authenticator, ChatPostMessage.class);

        LessRestlet.init(rootDir);
        app.get("/css/{file}.css", LessRestlet.class);
    }

    @Override
    public void service(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
        try {
            app.handle((HttpServletRequest) req, (HttpServletResponse) resp);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
