package com.altamiracorp.reddawn.web.routes.workspace;

import com.altamiracorp.reddawn.RedDawnSession;
import com.altamiracorp.reddawn.model.user.User;
import com.altamiracorp.reddawn.model.workspace.Workspace;
import com.altamiracorp.reddawn.model.workspace.WorkspaceRepository;
import com.altamiracorp.reddawn.model.workspace.WorkspaceRowKey;
import com.altamiracorp.reddawn.web.DevBasicAuthenticator;
import com.altamiracorp.reddawn.web.Responder;
import com.altamiracorp.reddawn.web.WebApp;
import com.altamiracorp.web.App;
import com.altamiracorp.web.AppAware;
import com.altamiracorp.web.Handler;
import com.altamiracorp.web.HandlerChain;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WorkspaceSave implements Handler, AppAware {
    private static final String DEFAULT_WORKSPACE_TITLE = "Default";
    private WorkspaceRepository workspaceRepository = new WorkspaceRepository();
    private static final Logger LOGGER = LoggerFactory
            .getLogger(WorkspaceSave.class.getName());
    private WebApp app;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        RedDawnSession session = app.getRedDawnSession(request);
        String data = request.getParameter("data");
        String workspaceRowKeyString = (String) request.getAttribute("workspaceRowKey");

        Workspace workspace;
        if (workspaceRowKeyString == null) {
            workspace = handleNew(request);
        } else {
            workspace = new Workspace(new WorkspaceRowKey(workspaceRowKeyString));
        }

        LOGGER.info("Saving workspace: " + workspace.getRowKey() + "\ntitle: " + workspace.getMetadata().getTitle() + "\ndata: " + data);


        if (data != null) {
            workspace.getContent().setData(data);
        }


        workspaceRepository.save(session.getModelSession(), workspace);
        JSONObject resultJson = new JSONObject();
        resultJson.put("_rowKey", workspace.getRowKey().toString());
        resultJson.put("title", workspace.getMetadata().getTitle());

        new Responder(response).respondWith(resultJson);
    }

    public Workspace handleNew(HttpServletRequest request) {
        User currentUser = DevBasicAuthenticator.getUser(request);
        WorkspaceRowKey workspaceRowKey = new WorkspaceRowKey(
                currentUser.getRowKey().toString(), String.valueOf(System.currentTimeMillis()));
        Workspace workspace = new Workspace(workspaceRowKey);
        String title = request.getParameter("title");

        if (title != null) {
            workspace.getMetadata().setTitle(title);
        } else {
            workspace.getMetadata().setTitle(DEFAULT_WORKSPACE_TITLE);
        }

        return workspace;
    }

    @Override
    public void setApp(App app) {
        this.app = (WebApp) app;
    }
}
