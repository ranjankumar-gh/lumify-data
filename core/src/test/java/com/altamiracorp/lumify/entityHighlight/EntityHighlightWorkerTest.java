package com.altamiracorp.lumify.entityHighlight;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.altamiracorp.lumify.AppSession;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.powermock.modules.junit4.PowerMockRunner;

import com.altamiracorp.lumify.model.ModelSession;
import com.altamiracorp.lumify.ucd.artifact.Artifact;
import com.altamiracorp.lumify.ucd.artifact.ArtifactContent;
import com.altamiracorp.lumify.ucd.artifact.ArtifactRepository;

@RunWith(PowerMockRunner.class)
public class EntityHighlightWorkerTest {

    private static final String HIGHLIGHTER_FIELD = "highlighter";
    private static final String ARTIFACT_REPO_FIELD = "artifactRepository";
    private static final String HIGHLIGHTED_TEXT = "highlighted!";
    private static final String ARTIFACT_KEY = "FOO";

    private AppSession sessionMock;
    private ArtifactRepository artifactRepoMock;
    private EntityHighlighter highlighterMock;
    private Artifact artifactMock;
    private ArtifactContent contentMock;

    private EntityHighlightWorker worker;


    @Before
    public void setupTests() {
        sessionMock = mock(AppSession.class);
        artifactRepoMock = mock(ArtifactRepository.class);
        highlighterMock = mock(EntityHighlighter.class);
        artifactMock = mock(Artifact.class);
        contentMock = mock(ArtifactContent.class);

        worker = new EntityHighlightWorker(sessionMock, ARTIFACT_KEY);
    }

    @Test(expected = NullPointerException.class)
    public void testHighlightWorkerCreationInvalidSession() {
        new EntityHighlightWorker(null, ARTIFACT_KEY);
    }

    @Test(expected = NullPointerException.class)
    public void testHighlightWorkerCreationInvalidArtifactKey() {
        new EntityHighlightWorker(sessionMock, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHighlightWorkerCreationEmptyArtifactKey() {
        new EntityHighlightWorker(sessionMock, "");
    }

    @Test
    public void testHighlightInvalidArtifact() {
        // Configure the repo to return an invalid artifact
        when(artifactRepoMock.findByRowKey(any(ModelSession.class), anyString())).thenReturn(null);

        Whitebox.setInternalState(worker, ARTIFACT_REPO_FIELD, artifactRepoMock);

        worker.run();
        verify(sessionMock, times(1)).getModelSession();
        verify(artifactRepoMock, times(1)).findByRowKey(any(ModelSession.class), anyString());
    }

    @Test
    public void testArtifactInvalidHighlightText() {
        // Configure the repo to return an invalid artifact
        when(artifactRepoMock.findByRowKey(any(ModelSession.class), anyString())).thenReturn(artifactMock);

        when(highlighterMock.getHighlightedText(any(AppSession.class), any(Artifact.class))).thenReturn(null);

        Whitebox.setInternalState(worker, ARTIFACT_REPO_FIELD, artifactRepoMock);
        Whitebox.setInternalState(worker, HIGHLIGHTER_FIELD, highlighterMock);

        worker.run();
        verify(sessionMock, times(1)).getModelSession();
        verify(artifactRepoMock, times(1)).findByRowKey(any(ModelSession.class), anyString());
    }


    @Test
    public void testArtifactEmptyHighlightText() {
        // Configure the repo to return an invalid artifact
        when(artifactRepoMock.findByRowKey(any(ModelSession.class), anyString())).thenReturn(artifactMock);

        when(highlighterMock.getHighlightedText(any(AppSession.class), any(Artifact.class))).thenReturn("");

        Whitebox.setInternalState(worker, ARTIFACT_REPO_FIELD, artifactRepoMock);
        Whitebox.setInternalState(worker, HIGHLIGHTER_FIELD, highlighterMock);

        worker.run();
        verify(sessionMock, times(1)).getModelSession();
        verify(artifactRepoMock, times(1)).findByRowKey(any(ModelSession.class), anyString());
    }

    @Test
    public void testArtifactHighlightText() {
        // Configure the repo to return an invalid artifact
        when(artifactRepoMock.findByRowKey(any(ModelSession.class), anyString())).thenReturn(artifactMock);
        when(artifactMock.getContent()).thenReturn(contentMock);
        when(highlighterMock.getHighlightedText(any(AppSession.class), any(Artifact.class))).thenReturn(HIGHLIGHTED_TEXT);

        Whitebox.setInternalState(worker, ARTIFACT_REPO_FIELD, artifactRepoMock);
        Whitebox.setInternalState(worker, HIGHLIGHTER_FIELD, highlighterMock);

        worker.run();
        verify(sessionMock, times(2)).getModelSession();
        verify(artifactRepoMock, times(1)).findByRowKey(any(ModelSession.class), anyString());
        verify(contentMock, times(1)).setHighlightedText(HIGHLIGHTED_TEXT);
        verify(artifactRepoMock, times(1)).save(any(ModelSession.class), any(Artifact.class));
    }
}
