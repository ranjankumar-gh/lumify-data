package com.altamiracorp.reddawn.cmdline;

import com.altamiracorp.reddawn.RedDawnSession;
import com.altamiracorp.reddawn.model.Session;
import com.altamiracorp.reddawn.model.graph.GraphVertex;
import com.altamiracorp.reddawn.model.ontology.Concept;
import com.altamiracorp.reddawn.model.ontology.OntologyRepository;
import com.altamiracorp.reddawn.model.ontology.PropertyType;
import com.altamiracorp.reddawn.model.resources.ResourceRepository;
import org.apache.accumulo.core.util.CachedConfiguration;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OwlImport extends RedDawnCommandLineBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(OwlImport.class.getName());
    private OntologyRepository ontologyRepository = new OntologyRepository();
    private ResourceRepository resourceRepository = new ResourceRepository();
    private String inFileName;
    private File inDir;

    public static void main(String[] args) throws Exception {
        int res = ToolRunner.run(CachedConfiguration.getInstance(), new OwlImport(), args);
        if (res != 0) {
            System.exit(res);
        }
    }

    @Override
    protected Options getOptions() {
        Options options = super.getOptions();

        options.addOption(
                OptionBuilder
                        .withLongOpt("in")
                        .withDescription("The input OWL file")
                        .isRequired()
                        .hasArg(true)
                        .withArgName("fileName")
                        .create("i")
        );

        return options;
    }

    @Override
    protected void processOptions(CommandLine cmd) throws Exception {
        super.processOptions(cmd);
        this.inFileName = cmd.getOptionValue("in");
    }

    @Override
    protected int run(CommandLine cmd) throws Exception {
        RedDawnSession session = createRedDawnSession();
        session.initialize();

        File inFile = new File(inFileName);
        importFile(session, inFile);

        return 0;
    }

    public void importFile(RedDawnSession session, File inFile) throws ParserConfigurationException, SAXException, IOException {
        inDir = inFile.getParentFile();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inFile);

        Element rootElem = doc.getDocumentElement();
        NodeList rootElemChildNodes = rootElem.getChildNodes();
        for (int i = 0; i < rootElemChildNodes.getLength(); i++) {
            Node child = rootElemChildNodes.item(i);
            if (child instanceof Element) {
                Element childElem = (Element) child;
                if (childElem.getNamespaceURI().equals("http://www.w3.org/2002/07/owl#") && childElem.getLocalName().equals("Class")) {
                    importClassElement(session, childElem);
                } else if (childElem.getNamespaceURI().equals("http://www.w3.org/2002/07/owl#") && childElem.getLocalName().equals("DatatypeProperty")) {
                    importDatatypePropertyElement(session, childElem);
                } else if (childElem.getNamespaceURI().equals("http://www.w3.org/2002/07/owl#") && childElem.getLocalName().equals("ObjectProperty")) {
                    importObjectPropertyElement(session, childElem);
                }
            }
        }
    }

    private void importClassElement(RedDawnSession session, Element classElem) {
        String about = getName(classElem.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about"));
        Element labelElem = getEnglishLanguageLabel(classElem);
        String labelText = labelElem.getTextContent().trim();
        Element subClassOf = getSingleChildElement(classElem, "http://www.w3.org/2000/01/rdf-schema#", "subClassOf");
        String subClassOfResource = subClassOf.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");
        List<Element> propertyElems = getChildElements(classElem, "http://altamiracorp.com/ontology#", "property");

        String parentName = getName(subClassOfResource);

        LOGGER.info("importClassElement: about: " + about + ", labelText: " + labelText + ", parentName: " + parentName);
        Concept parent = ontologyRepository.getConceptByName(session.getGraphSession(), parentName);
        if (parent == null) {
            throw new RuntimeException("Could not find parent " + parentName + " for " + about);
        }
        Concept concept = ontologyRepository.getOrCreateConcept(session.getGraphSession(), parent, about, labelText);

        for (Element propertyElem : propertyElems) {
            String propertyName = propertyElem.getAttributeNS("http://altamiracorp.com/ontology#", "name");
            String propertyValue = propertyElem.getTextContent().trim();
            LOGGER.info("  " + propertyName + " = " + propertyValue);
            if (propertyName.equals("glyphIconFileName")) {
                propertyName = "_glyphIcon";
                propertyValue = importGlyphIconFile(session.getModelSession(), propertyValue);
            }
            concept.setProperty(propertyName, propertyValue);
        }
        session.getGraphSession().commit();
    }

    private Element getEnglishLanguageLabel(Element elem) {
        List<Element> childElems = getChildElements(elem, "http://www.w3.org/2000/01/rdf-schema#", "label");
        for (Element childElem : childElems) {
            String attr = childElem.getAttributeNS(OwlExport.NS_XML_URI, "lang");
            if (attr.equals("en")) {
                return childElem;
            }
        }
        throw new RuntimeException("Could not find english label on element " + elem.getTagName());
    }

    private String importGlyphIconFile(Session session, String fileName) {
        File f = new File(inDir, fileName);

        LOGGER.info("  importing file: " + fileName);
        String id = resourceRepository.importFile(session, f.getAbsolutePath());
        LOGGER.info("  resource key: " + id);
        return id;
    }

    private void importDatatypePropertyElement(RedDawnSession session, Element datatypePropertyElem) {
        String about = datatypePropertyElem.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about");
        Element labelElem = getEnglishLanguageLabel(datatypePropertyElem);
        String labelText = labelElem.getTextContent().trim();
        Element domainElem = getSingleChildElement(datatypePropertyElem, "http://www.w3.org/2000/01/rdf-schema#", "domain");
        String domainResource = domainElem.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");
        Element rangeElem = getSingleChildElement(datatypePropertyElem, "http://www.w3.org/2000/01/rdf-schema#", "range");
        String rangeResource = rangeElem.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");

        String domainResourceName = getName(domainResource);
        String rangeResourceName = getName(rangeResource);

        LOGGER.info("importDatatypePropertyElement: about: " + about + ", labelText: " + labelText + ", domainResourceName: " + domainResourceName + ", rangeResourceName: " + rangeResourceName);
        GraphVertex domain = ontologyRepository.getGraphVertexByTitle(session.getGraphSession(), domainResourceName);
        PropertyType propertyType = PropertyType.convert(rangeResourceName);
        session.getGraphSession().commit();

        ontologyRepository.addPropertyTo(session.getGraphSession(), domain, about, labelText, propertyType);
        session.getGraphSession().commit();
    }

    private void importObjectPropertyElement(RedDawnSession session, Element objectPropertyElem) {
        String about = objectPropertyElem.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about");
        Element labelElem = getEnglishLanguageLabel(objectPropertyElem);
        String labelText = labelElem.getTextContent().trim();
        Element domainElem = getSingleChildElement(objectPropertyElem, "http://www.w3.org/2000/01/rdf-schema#", "domain");
        String domainResource = domainElem.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");
        Element rangeElem = getSingleChildElement(objectPropertyElem, "http://www.w3.org/2000/01/rdf-schema#", "range");
        String rangeResource = rangeElem.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");

        String domainResourceName = getName(domainResource);
        String rangeResourceName = getName(rangeResource);

        LOGGER.info("importObjectPropertyElement: about: " + about + ", labelText: " + labelText + ", domainResourceName: " + domainResourceName + ", rangeResourceName: " + rangeResourceName);
        GraphVertex domain = ontologyRepository.getGraphVertexByTitle(session.getGraphSession(), domainResourceName);
        GraphVertex range = ontologyRepository.getGraphVertexByTitle(session.getGraphSession(), rangeResourceName);

        ontologyRepository.getOrCreateRelationshipType(session.getGraphSession(), domain, range, about, labelText);
        session.getGraphSession().commit();
    }

    private Element getSingleChildElement(Element elem, String ns, String localName) {
        List<Element> childElems = getChildElements(elem, ns, localName);
        if (childElems.size() == 0) {
            return null;
        }
        if (childElems.size() > 1) {
            throw new RuntimeException("More than one child found with " + ns + ":" + localName + " on element " + elem.getTagName());
        }
        return childElems.get(0);
    }

    private List<Element> getChildElements(Element elem, String ns, String localName) {
        List<Element> childElems = new ArrayList<Element>();
        NodeList children = elem.getElementsByTagNameNS(ns, localName);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                if (child.getLocalName().equals(localName)) {
                    childElems.add((Element) child);
                }
            }
        }
        return childElems;
    }

    private String getName(String s) {
        if (s.startsWith("#")) {
            return s.substring("#".length());
        }

        int lastSlash = s.lastIndexOf('/');
        if (lastSlash > 0) {
            return s.substring(lastSlash + 1);
        }

        return s;
    }
}
