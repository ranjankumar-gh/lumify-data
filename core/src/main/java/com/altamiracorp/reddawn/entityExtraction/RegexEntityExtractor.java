package com.altamiracorp.reddawn.entityExtraction;

import com.altamiracorp.reddawn.model.termMention.TermMention;
import com.altamiracorp.reddawn.model.termMention.TermMentionRowKey;
import com.altamiracorp.reddawn.ucd.artifact.Artifact;
import org.apache.hadoop.mapreduce.Mapper.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexEntityExtractor extends EntityExtractor {
    private static final String REGULAR_EXPRESSION = "regularExpression";
    private static final String ENTITY_TYPE = "entityType";

    private Pattern pattern;
    private String entityType;

    @Override
    public void setup(Context context) throws IOException {
        String regularExpression = context.getConfiguration().get(REGULAR_EXPRESSION);
        if (regularExpression == null) {
            throw new IOException("No regular expression was provided!");
        }

        this.pattern = Pattern.compile(regularExpression, Pattern.MULTILINE);

        entityType = context.getConfiguration().get(ENTITY_TYPE);
        if (entityType == null) {
            throw new IOException("No entity type for this regular expression was provided!");
        }
    }

    @Override
    List<TermMention> extract(Artifact artifact, String text) throws Exception {
        ArrayList<TermMention> terms = new ArrayList<TermMention>();
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String name = matcher.group();
            int start = matcher.start();
            int end = matcher.end();
            TermMention termMention = new TermMention(new TermMentionRowKey(artifact.getRowKey().toString(), start, end));
            termMention.getMetadata().setConcept(entityType);
            termMention.getMetadata().setSign(name);
            terms.add (termMention);
        }
        return terms;
    }
}
