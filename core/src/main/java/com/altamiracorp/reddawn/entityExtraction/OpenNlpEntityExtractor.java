package com.altamiracorp.reddawn.entityExtraction;

import com.altamiracorp.reddawn.ucd.sentence.Sentence;
import com.altamiracorp.reddawn.ucd.sentence.SentenceRowKey;
import com.altamiracorp.reddawn.ucd.term.Term;
import com.altamiracorp.reddawn.ucd.term.TermMention;
import com.altamiracorp.reddawn.ucd.term.TermRowKey;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class OpenNlpEntityExtractor implements EntityExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenNlpEntityExtractor.class.getName());

    private FileSystem fs;
    private String pathPrefix;

    private Tokenizer tokenizer;
    private List<TokenNameFinder> finders;

    private static final String PATH_PREFIX_CONFIG = "nlpConfPathPrefix";
    private static final String DEFAULT_PATH_PREFIX = "hdfs://";
    private static final int NEW_LINE_CHARACTER_LENGTH = 1;
    private static final String EXTRACTOR_ID = "OpenNLP";

    @Override
    public void setup(Context context) throws IOException {
        this.pathPrefix = context.getConfiguration().get(PATH_PREFIX_CONFIG,
                DEFAULT_PATH_PREFIX);
        this.fs = FileSystem.get(context.getConfiguration());

        setTokenizer(loadTokenizer());
        setFinders(loadFinders());
    }

    @Override
    public Collection<Term> extract(Sentence sentence)
            throws Exception {
        SentenceRowKey sentenceRowKey = sentence.getRowKey();
        String text = sentence.getData().getText();
        LOGGER.info("Extracting entities from sentence: " + sentenceRowKey.toString());
        ArrayList<Term> terms = new ArrayList<Term>();
        ObjectStream<String> untokenizedLineStream = new PlainTextByLineStream(new StringReader(text));
        String line;
        Long charOffset = sentence.getData().getStart();
        while ((line = untokenizedLineStream.read()) != null) {
            ArrayList<Term> newTerms = processLine(sentence, line, charOffset);
            terms.addAll(newTerms);
            charOffset += line.length() + NEW_LINE_CHARACTER_LENGTH;
        }
        return terms;
    }

    private ArrayList<Term> processLine(Sentence sentence, String line, Long charOffset) {
        ArrayList<Term> terms = new ArrayList<Term>();
        String tokenList[] = tokenizer.tokenize(line);
        Span[] tokenListPositions = tokenizer.tokenizePos(line);
        for (TokenNameFinder finder : finders) {
            Span[] foundSpans = finder.find(tokenList);
            for (Span span : foundSpans) {
                Term term = createTerm(sentence, charOffset, span, tokenList, tokenListPositions);
                terms.add(term);
            }
            finder.clearAdaptiveData();
        }
        return terms;
    }

    private Term createTerm(Sentence sentence, Long charOffset, Span foundName, String[] tokens, Span[] tokenListPositions) {
        String sign = Span.spansToStrings(new Span[]{foundName}, tokens)[0];
        Long termMentionStart = charOffset + tokenListPositions[foundName.getStart()].getStart();
        Long termMentionEnd = charOffset + tokenListPositions[foundName.getEnd() - 1].getEnd();

        String concept = openNlpTypeToConcept(foundName.getType());
        TermRowKey termKey = new TermRowKey(sign, getModelName(), mapConceptName(concept));
        TermMention termMention = new TermMention()
                .setArtifactKey(sentence.getData().getArtifactId())
                .setArtifactKeySign(sentence.getData().getArtifactId())
                .setAuthor(EXTRACTOR_ID)
                .setMentionStart(termMentionStart)
                .setMentionEnd(termMentionEnd)
                .setSentenceText(sentence.getData().getText())
                .setSentenceTokenOffset(sentence.getRowKey().getStartOffset())
                .setArtifactType(sentence.getMetadata().getArtifactType());

        if (sentence.getMetadata().getArtifactSubject() != null) {
            termMention.setArtifactSubject(sentence.getMetadata().getArtifactSubject());
        }

        setSecurityMarking(termMention, sentence);
        Term term = new Term(termKey)
                .addTermMention(termMention);
        return term;
    }

    protected String mapConceptName(String concept) {
        return concept.substring(0, 1).toUpperCase() + concept.substring(1);
    }

    private void setSecurityMarking(TermMention termMention, Sentence sentence) {
        String securityMarking = sentence.getMetadata().getSecurityMarking();
        if (securityMarking != null) {
            termMention.setSecurityMarking(sentence.getMetadata().getSecurityMarking());
        }
    }

    protected abstract List<TokenNameFinder> loadFinders() throws IOException;

    protected abstract String getModelName();

    protected String getPathPrefix() {
        return this.pathPrefix;
    }

    protected FileSystem getFS() {
        return this.fs;
    }

    protected Tokenizer loadTokenizer() throws IOException {
        Path tokenizerHdfsPath = new Path(pathPrefix
                + "/conf/opennlp/en-token.bin");

        TokenizerModel tokenizerModel = null;
        InputStream tokenizerModelInputStream = fs.open(tokenizerHdfsPath);
        try {
            tokenizerModel = new TokenizerModel(tokenizerModelInputStream);
        } finally {
            tokenizerModelInputStream.close();
        }

        return new TokenizerME(tokenizerModel);
    }

    protected void setFinders(List<TokenNameFinder> finders) {
        this.finders = finders;

    }

    protected void setTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    private String openNlpTypeToConcept(String type) {
        return type; // TODO create a mapping for OpenNLP to UCD concepts
    }

}
