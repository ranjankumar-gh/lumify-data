package com.altamiracorp.lumify.web;

import com.altamiracorp.lumify.core.user.User;
import com.altamiracorp.web.Handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public abstract class AuthenticationProvider implements Handler {
    private static final String CURRENT_USER_REQ_ATTR_NAME = "user.current";

    protected void setUser(HttpServletRequest request, User user) {
        request.getSession().setAttribute(AuthenticationProvider.CURRENT_USER_REQ_ATTR_NAME, user);
    }

    public static User getUser(HttpSession session) {
        Object user = session.getAttribute(AuthenticationProvider.CURRENT_USER_REQ_ATTR_NAME);
        return user != null ? (User) user : null;
    }

    public static User getUser(HttpServletRequest request) {
        return AuthenticationProvider.getUser(request.getSession());
    }
}
