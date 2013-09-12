package com.altamiracorp.lumify.web.routes.admin;

import com.altamiracorp.lumify.AppSession;
import com.altamiracorp.lumify.web.BaseRequestHandler;
import com.altamiracorp.web.HandlerChain;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class AdminTables extends BaseRequestHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        AppSession session = app.getAppSession(request);

        List<String> tables = session.getModelSession().getTableList();

        JSONObject results = new JSONObject();
        JSONArray tablesJson = new JSONArray();
        for (String table : tables) {
            tablesJson.put(table);
        }
        results.put("tables", tablesJson);

        respondWithJson(response, results);
    }
}
