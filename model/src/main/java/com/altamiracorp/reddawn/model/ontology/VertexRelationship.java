package com.altamiracorp.reddawn.model.ontology;

import com.tinkerpop.blueprints.Vertex;

public class VertexRelationship extends Relationship {
    private final Vertex vertex;

    public VertexRelationship(Vertex vertex) {
        this.vertex = vertex;
    }

    @Override
    public String getId() {
        return getVertex().getId().toString();
    }

    @Override
    public String getTitle() {
        return getVertex().getProperty(PropertyName.ONTOLOGY_TITLE.toString());
    }

    public Vertex getVertex() {
        return vertex;
    }
}
