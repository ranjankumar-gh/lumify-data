package com.altamiracorp.reddawn.structuredDataExtraction;

import com.altamiracorp.reddawn.ConfigurableMapJobBase;
import com.altamiracorp.reddawn.RedDawnMapper;
import com.altamiracorp.reddawn.model.AccumuloModelOutputFormat;
import com.altamiracorp.reddawn.model.graph.GraphRelationship;
import com.altamiracorp.reddawn.model.graph.GraphVertex;
import com.altamiracorp.reddawn.model.ontology.Concept;
import com.altamiracorp.reddawn.model.ontology.OntologyRepository;
import com.altamiracorp.reddawn.ucd.AccumuloArtifactInputFormat;
import com.altamiracorp.reddawn.ucd.artifact.Artifact;
import com.altamiracorp.reddawn.ucd.artifact.ArtifactRepository;
import com.altamiracorp.reddawn.ucd.sentence.Sentence;
import com.altamiracorp.reddawn.ucd.sentence.SentenceRepository;
import com.altamiracorp.reddawn.ucd.term.Term;
import com.altamiracorp.reddawn.ucd.term.TermMention;
import com.altamiracorp.reddawn.ucd.term.TermRepository;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.util.ToolRunner;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class StructuredDataExtractionMR extends ConfigurableMapJobBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredDataExtractionMR.class.getName());

    @Override
    protected Class<? extends Mapper> getMapperClass(Job job, Class clazz) {
        return StructuredDataExtractorMapper.class;
    }

    @Override
    protected Class<? extends InputFormat> getInputFormatClassAndInit(Job job) {
        AccumuloArtifactInputFormat.init(job, getUsername(), getPassword(), getAuthorizations(), getZookeeperInstanceName(), getZookeeperServerNames());
        return AccumuloArtifactInputFormat.class;
    }

    @Override
    protected Class<? extends OutputFormat> getOutputFormatClass() {
        return AccumuloModelOutputFormat.class;
    }

    public static class StructuredDataExtractorMapper extends RedDawnMapper<Text, Artifact, Text, Sentence> {
        private HashMap<String, StructuredDataExtractorBase> structuredDataExtrators = new HashMap<String, StructuredDataExtractorBase>();
        private ArtifactRepository artifactRepository = new ArtifactRepository();
        private SentenceRepository sentenceRepository = new SentenceRepository();
        private TermRepository termRepository = new TermRepository();
        private OntologyRepository ontologyRepository = new OntologyRepository();

        public StructuredDataExtractorMapper() {
            structuredDataExtrators.put("csv", new CsvStructuredDataExtractor());
        }

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            try {
                for (StructuredDataExtractorBase structuredDataExtractor : structuredDataExtrators.values()) {
                    structuredDataExtractor.setup(context);
                }
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        protected void safeMap(Text key, Artifact artifact, Context context) throws Exception {
            JSONObject mappingJson = artifact.getGenericMetadata().getMappingJson();
            if (mappingJson == null) {
                return;
            }
            String text = artifact.getContent().getDocExtractedTextString();
            if (text == null || text.length() == 0) {
                return;
            }
            String structuredDataType = mappingJson.getString("type");
            LOGGER.info("Extracting structured data from: " + artifact.getRowKey().toString() + ", type: " + structuredDataType);

            StructuredDataExtractorBase structuredDataExtractor = structuredDataExtrators.get(structuredDataType);
            if (structuredDataExtractor != null) {
                ExtractedData extractedData = structuredDataExtractor.extract(artifact, text, mappingJson);

                saveToUcd(artifact, extractedData);
                GraphVertex artifactVertex = saveArtifactToGraph(artifact);
                saveTermsToGraph(extractedData.getTerms(), artifactVertex);
                saveRelationships(extractedData.getRelationships());
            } else {
                throw new Exception("Unknown or unhandled structured data type: " + structuredDataType);
            }
        }

        private void saveToUcd(Artifact artifact, ExtractedData extractedData) {
            artifactRepository.save(getSession().getModelSession(), artifact);
            sentenceRepository.saveMany(getSession().getModelSession(), extractedData.getSentences());

            for (Term term : extractedData.getTerms()) {
                if (term == null) {
                    continue;
                }
                String termRowKey = term.getRowKey().toString();
                Term existingTerm = termRepository.findByRowKey(getSession().getModelSession(), termRowKey);
                if (existingTerm != null) {
                    existingTerm.update(term);
                    termRepository.save(getSession().getModelSession(), existingTerm);
                    term.update(existingTerm);
                } else {
                    termRepository.save(getSession().getModelSession(), term);
                }
            }
        }

        private GraphVertex saveArtifactToGraph(Artifact artifact) {
            GraphVertex artifactVertex = artifactRepository.saveToGraph(getSession().getModelSession(), getSession().getGraphSession(), artifact);
            getSession().getGraphSession().commit();
            return artifactVertex;
        }

        private void saveTermsToGraph(List<Term> terms, GraphVertex artifactVertex) {
            HashMap<String, Concept> conceptMap = new HashMap<String, Concept>();

            for (Term term : terms) {
                if (term == null) {
                    continue;
                }
                String conceptLabel = term.getRowKey().getConceptLabel();
                Concept concept = conceptMap.get(conceptLabel);
                if (concept == null) {
                    concept = ontologyRepository.getConceptByName(getSession().getGraphSession(), conceptLabel);
                    if (concept == null) {
                        throw new RuntimeException("Could not find concept: " + conceptLabel);
                    }
                    conceptMap.put(conceptLabel, concept);
                }

                for (TermMention termMention : term.getTermMentions()) {
                    termRepository.saveToGraph(getSession().getModelSession(), getSession().getGraphSession(), term, termMention, concept.getId(), artifactVertex);
                }
            }
            getSession().getGraphSession().commit();
        }

        private void saveRelationships(List<StructuredDataRelationship> relationships) {
            for (StructuredDataRelationship relationship : relationships) {
                String sourceVertexId = relationship.getTermMentionSource().getGraphVertexId();
                String destVertexId = relationship.getTermMentionDest().getGraphVertexId();
                String label = relationship.getLabel();
                GraphRelationship graphRelationship = new GraphRelationship(null, sourceVertexId, destVertexId, label);
                getSession().getGraphSession().save(graphRelationship);
            }
        }
    }

    @Override
    protected boolean hasConfigurableClassname() {
        return false;
    }

    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(new StructuredDataExtractionMR(), args);
        if (res != 0) {
            System.exit(res);
        }
    }
}
