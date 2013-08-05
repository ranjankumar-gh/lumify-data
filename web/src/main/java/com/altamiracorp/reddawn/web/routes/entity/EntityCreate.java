package com.altamiracorp.reddawn.web.routes.entity;

import com.altamiracorp.reddawn.RedDawnSession;
import com.altamiracorp.reddawn.entityHighlight.TermAndTermMentionOffsetItem;
import com.altamiracorp.reddawn.ucd.term.Term;
import com.altamiracorp.reddawn.ucd.term.TermAndTermMention;
import com.altamiracorp.reddawn.ucd.term.TermMention;
import com.altamiracorp.reddawn.ucd.term.TermRepository;
import com.altamiracorp.reddawn.web.Responder;
import com.altamiracorp.reddawn.web.User;
import com.altamiracorp.reddawn.web.WebApp;
import com.altamiracorp.web.App;
import com.altamiracorp.web.AppAware;
import com.altamiracorp.web.Handler;
import com.altamiracorp.web.HandlerChain;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

public class EntityCreate implements Handler, AppAware {
    private static final String MODEL_KEY = "manual";
    private WebApp app;
    private TermRepository termRepository = new TermRepository();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        User currentUser = User.getUser(request);
        RedDawnSession session = app.getRedDawnSession(request);

        // validate parameters
        String artifactKey = request.getParameter("artifactKey");
        if (artifactKey == null) {
            throw new RuntimeException("'artifactKey' is required.");
        }

        if (request.getParameter("mentionStart") == null) {
            throw new RuntimeException("'mentionStart' is required.");
        }
        long mentionStart = Long.parseLong(request.getParameter("mentionStart"));

        if (request.getParameter("mentionEnd") == null) {
            throw new RuntimeException("'mentionEnd' is required.");
        }
        long mentionEnd = Long.parseLong(request.getParameter("mentionEnd"));

        String sign = request.getParameter("sign");
        if (sign == null) {
            throw new RuntimeException("'sign' is required.");
        }

        String conceptLabel = request.getParameter("conceptLabel");
        if (conceptLabel == null) {
            throw new RuntimeException("'conceptLabel' is required.");
        }

        String newObjectSign = request.getParameter("newObjectSign");

        if (newObjectSign != null) {
            sign = newObjectSign;
        }

        // do the work
        TermAndTermMention termAndTermMention = createTerm(currentUser, artifactKey, mentionStart, mentionEnd, sign, conceptLabel);

        termRepository.save(session.getModelSession(), termAndTermMention.getTerm());
        termRepository.saveToGraph(session.getModelSession(), session.getGraphSession(), termAndTermMention.getTerm(), termAndTermMention.getTermMention());

        TermAndTermMentionOffsetItem offsetItem = new TermAndTermMentionOffsetItem(termAndTermMention);
        new Responder(response).respondWith(offsetItem.toJson());
    }

    private static TermAndTermMention createTerm(User currentUser, String artifactKey, long mentionStart, long mentionEnd, String sign, String conceptLabel) {
        Term term = new Term(sign, MODEL_KEY, conceptLabel);
        TermMention termMention = new TermMention()
                .setArtifactKey(artifactKey)
                .setMentionStart(mentionStart)
                .setMentionEnd(mentionEnd)
                .setAuthor(currentUser.getUsername())
                .setDate(new Date());
        term.addTermMention(termMention);
        return new TermAndTermMention(term, termMention);
    }

    public void setApp(App app) {
        this.app = (WebApp) app;
    }
}
