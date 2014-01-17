package com.altamiracorp.lumify.core.model.ontology;

public enum PropertyName {
    CONCEPT_TYPE("_conceptType"),
    DATA_TYPE("_dataType"),
    TITLE("title"),
    ONTOLOGY_TITLE("ontologyTitle"),
    DISPLAY_NAME("displayName"),
    DISPLAY_TYPE("displayType"),
    GEO_LOCATION("geoLocation"),
    GEO_LOCATION_DESCRIPTION("_geoLocationDescription"),
    ROW_KEY("_rowKey"),
    GLYPH_ICON("_glyphIcon"),
    MAP_GLYPH_ICON("_mapGlyphIcon"),
    COLOR("_color"),
    SOURCE("source"),
    PROCESS("_process"),
    START_DATE("startDate"),
    END_DATE("endDate"),
    RELATIONSHIP_TYPE("relationshipType"),
    PUBLISHED_DATE("publishedDate"),
    CREATE_DATE("_createDate"),
    FILE_NAME("_fileName"),
    FILE_NAME_EXTENSION("_fileNameExtension"),
    TIME_STAMP("_timeStamp"),
    RAW("_raw"),
    VIDEO("_video"),
    VIDEO_SIZE("_videoSize"),
    RAW_POSTER_FRAME("_rawPosterFrame"),
    DETECTED_OBJECTS("_detectedObjects"),
    MIME_TYPE("_mimeType"),
    VIDEO_PREVIEW_IMAGE("videoPreviewImage"),
    HIGHLIGHTED_TEXT("highlightedText"),
    AUTHOR("author");

    private final String text;

    PropertyName(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return this.text;
    }
}
