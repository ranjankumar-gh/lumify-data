package com.altamiracorp.reddawn.statementExtraction;

import com.altamiracorp.reddawn.model.AccumuloQueryUser;
import com.altamiracorp.reddawn.model.AccumuloSession;
import com.altamiracorp.reddawn.model.Session;
import com.altamiracorp.reddawn.ucd.artifact.Artifact;
import com.altamiracorp.reddawn.ucd.artifact.ArtifactRepository;
import com.altamiracorp.reddawn.ucd.sentence.Sentence;
import com.altamiracorp.reddawn.ucd.sentence.SentenceRepository;
import junit.framework.Assert;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class StatementExtractionTest {
    @Test
    @Ignore
    public void testArtifactCounts() throws AccumuloSecurityException, AccumuloException {
        Session session = createModelSession();
        ArtifactRepository artifactRepository = new ArtifactRepository();
        List<Artifact> rows = artifactRepository.findAll(session);
        assertEquals(233, rows.size());
    }

    @Test
    @Ignore
    public void testSentenceCounts() throws AccumuloSecurityException, AccumuloException {
        Session session = createModelSession();
        SentenceRepository sentenceRepository = new SentenceRepository();
        List<Sentence> rows = sentenceRepository.findAll(session);
        assertEquals(0, rows.size());
    }

    private Session createModelSession() throws AccumuloException, AccumuloSecurityException {
        String zookeeperInstanceName = "reddawn";
        String zookeeperServerName = "192.168.33.10";
        String username = "root";
        String password = "reddawn";
        ZooKeeperInstance zooKeeperInstance = new ZooKeeperInstance(zookeeperInstanceName, zookeeperServerName);
        Connector connector = zooKeeperInstance.getConnector(username, password);

        AccumuloQueryUser queryUser = new AccumuloQueryUser();
        return new AccumuloSession(connector, queryUser);
    }

}
