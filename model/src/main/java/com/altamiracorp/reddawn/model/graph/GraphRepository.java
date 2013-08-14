package com.altamiracorp.reddawn.model.graph;

import com.altamiracorp.reddawn.model.GraphSession;
import com.altamiracorp.reddawn.model.ontology.VertexType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphRepository {
    public GraphVertex findVertex(GraphSession graphSession, String graphVertexId) {
        return graphSession.findGraphVertex(graphVertexId);
    }

    public GraphVertex findVertexByTitleAndType(GraphSession graphSession, String graphVertexTitle, VertexType type) {
        return graphSession.findVertexByExactTitleAndType(graphVertexTitle, type);
    }

    public List<GraphVertex> getRelatedVertices(GraphSession graphSession, String graphVertexId) {
        return graphSession.getRelatedVertices(graphVertexId);
    }

    public List<GraphRelationship> getRelationships(GraphSession graphSession, List<String> allIds) {
        return graphSession.getRelationships(allIds);
    }

    public void saveMany(GraphSession graphSession, List<GraphRelationship> graphRelationships) {
        for (GraphRelationship graphRelationship : graphRelationships) {
            save(graphSession, graphRelationship);
        }
    }

    private GraphRelationship save(GraphSession graphSession, GraphRelationship graphRelationship) {
        graphSession.save(graphRelationship);
        return graphRelationship;
    }

    public GraphRelationship saveRelationship(GraphSession graphSession, String sourceGraphVertexId, String destGraphVertexId, String label) {
        GraphRelationship relationship = new GraphRelationship(null, sourceGraphVertexId, destGraphVertexId, label);
        return save(graphSession, relationship);
    }

    public String saveVertex(GraphSession graphSession, GraphVertex graphVertex) {
        return graphSession.save(graphVertex);
    }

    public Map<String, String> getProperties(GraphSession graphSession, String graphVertexId) {
        return graphSession.getProperties(graphVertexId);
    }

    public Map<GraphRelationship, GraphVertex> getRelationships(GraphSession graphSession, String graphVertexId) {
        return graphSession.getRelationships(graphVertexId);
    }

    public HashMap<String, String> getEdgeProperties(GraphSession graphSession, String sourceVertex, String destVertex, String label) {
        return graphSession.getEdgeProperties(sourceVertex, destVertex, label);
    }

    public List<GraphVertex> findByGeoLocation(GraphSession graphSession, double latitude, double longitude, double radius) {
        return graphSession.findByGeoLocation(latitude, longitude, radius);
    }

    public List<GraphVertex> searchVerticesByTitle(GraphSession graphSession, String query) {
        return graphSession.searchVerticesByTitle(query);
    }

    public List<GraphVertex> searchVerticesByTitleAndType(GraphSession graphSession, String query, VertexType type) {
        return graphSession.searchVerticesByTitleAndType(query, type);
    }

    public List<GraphVertex> getResolvedRelatedVertices(GraphSession graphSession, String graphVertexId) {
        return graphSession.getResolvedRelatedVertices(graphVertexId);
    }

    public void removeRelationship(GraphSession graphSession, String source, String target, String label) {
        graphSession.removeRelationship(source, target, label);
        return;
    }
}
