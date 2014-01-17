package com.altamiracorp.demoaccountweb.routes;

import com.altamiracorp.demoaccountweb.security.AuthenticationProvider;
import com.altamiracorp.miniweb.HandlerChain;
import com.google.inject.Inject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CreateAccountForm extends BaseRequestHandler {
    private final AuthenticationProvider authenticationProvider;

    @Inject
    public CreateAccountForm(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        // FIXME
    }
}
