package com.altamiracorp.lumify.demoaccountweb.routes;

import com.altamiracorp.lumify.demoaccountweb.ApplicationConfiguration;
import com.altamiracorp.lumify.demoaccountweb.DemoAccountUserRepository;
import com.altamiracorp.lumify.demoaccountweb.model.DemoAccountUser;
import com.altamiracorp.miniweb.HandlerChain;
import com.google.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class AccountCreated extends BaseRequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountCreated.class.getName());
    private ApplicationConfiguration configuration;

    @Inject
    public void setConfiguration(ApplicationConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        String path = request.getServletContext().getRealPath(request.getPathInfo());

        Map<String, String> replacementTokens = new HashMap();
        replacementTokens.put("lumify-login-url", configuration.get(ApplicationConfiguration.LUMIFY_LOGIN_URL));
        replacementTokens.put("baseUrl", getBaseUrl(request));

        String contents = replaceTokens(FileUtils.readFileToString(new File(path + ".html")), replacementTokens);
        response.setContentType("text/html");
        response.getOutputStream().write(contents.getBytes());
    }
}