package io.lumify.classification;

import com.altamiracorp.miniweb.Handler;
import io.lumify.web.WebApp;
import io.lumify.web.WebAppPlugin;

import javax.servlet.ServletContext;

public class ClassificationWebAppPlugin implements WebAppPlugin {
    @Override
    public void init(WebApp app, ServletContext servletContext, Handler authenticationHandler) {
        app.registerCss("/io/lumify/classification/classification-plugin.css");
        app.registerJavaScript("/io/lumify/classification/classification-plugin.js");
        app.registerResourceBundle("/io/lumify/classification/messages.properties");
    }
}