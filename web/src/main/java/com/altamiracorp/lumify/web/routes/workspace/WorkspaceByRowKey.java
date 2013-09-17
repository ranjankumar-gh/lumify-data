package com.altamiracorp.lumify.web.routes.workspace;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.altamiracorp.lumify.AppSession;
import com.altamiracorp.lumify.model.Repository;
import com.altamiracorp.lumify.model.user.User;
import com.altamiracorp.lumify.model.workspace.Workspace;
import com.altamiracorp.lumify.model.workspace.WorkspaceRowKey;
import com.altamiracorp.lumify.web.BaseRequestHandler;
import com.altamiracorp.web.HandlerChain;
import com.google.inject.Inject;

public class WorkspaceByRowKey extends BaseRequestHandler {
    private final Repository<Workspace> workspaceRepository;
    private final Repository<User> userRepository;

    @Inject
    public WorkspaceByRowKey(final Repository<Workspace> workspaceRepo,
            final Repository<User> userRepo) {
        workspaceRepository = workspaceRepo;
        userRepository = userRepo;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        WorkspaceRowKey workspaceRowKey = new WorkspaceRowKey(getAttributeString(request, "workspaceRowKey"));
        AppSession session = app.getAppSession(request);

        User currentUser = getUser(request);
        if (!workspaceRowKey.toString().equals(currentUser.getMetadata().getCurrentWorkspace())) {
            currentUser.getMetadata().setCurrentWorkspace(workspaceRowKey.toString());
            userRepository.save(session.getModelSession(), currentUser);
        }

        Workspace workspace = workspaceRepository.findByRowKey(session.getModelSession(), workspaceRowKey.toString());

        if (workspace == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            JSONObject resultJSON = new JSONObject();
            resultJSON.put("id", workspace.getRowKey().toString());

            if (workspace.getContent().getData() != null) {
                resultJSON.put("data", new JSONObject(workspace.getContent().getData()));
            }

            respondWithJson(response, resultJSON);
        }

        chain.next(request, response);
    }
}
