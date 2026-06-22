package com.example.bpmn.service;

import com.example.bpmn.exception.CamundaIntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ZeebeCompatibilityService {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ZEEBE_NAMESPACE = "http://camunda.org/schema/zeebe/1.0";
    private static final String ZEEBE_PREFIX = "zeebe";
    private static final String XMLNS_ZEEBE = "xmlns:" + ZEEBE_PREFIX;

    public String enrich(String xml) {
        Document document = parse(xml);
        ensureZeebeNamespace(document);
        enrichExclusiveGatewayDefaults(document);
        enrichTaskDefinitions(document, "serviceTask", "service-task");
        enrichTaskDefinitions(document, "sendTask", "send-task");
        enrichTaskDefinitions(document, "receiveTask", "receive-task");
        enrichTaskDefinitions(document, "businessRuleTask", "business-rule-task");
        return write(document);
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml.stripLeading())));
        } catch (Exception ex) {
            throw new CamundaIntegrationException(HttpStatus.BAD_REQUEST,
                    "Invalid BPMN XML; unable to apply Zeebe compatibility enrichment", ex);
        }
    }

    private void ensureZeebeNamespace(Document document) {
        Element definitions = document.getDocumentElement();
        if (!ZEEBE_NAMESPACE.equals(definitions.getAttribute(XMLNS_ZEEBE))) {
            definitions.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLNS_ZEEBE, ZEEBE_NAMESPACE);
        }
    }

    private void enrichExclusiveGatewayDefaults(Document document) {

    NodeList gateways =
            document.getElementsByTagNameNS(
                    BPMN_NAMESPACE,
                    "exclusiveGateway");

    for (int i = 0; i < gateways.getLength(); i++) {

        Element gateway =
                (Element) gateways.item(i);

        if (hasText(
                gateway.getAttribute("default"))) {
            continue;
        }

        List<Element> outgoingFlows =
                findOutgoingSequenceFlows(
                        document,
                        gateway);

        if (outgoingFlows.size() <= 1) {
            continue;
        }

        Element defaultCandidate = null;

        for (Element flow : outgoingFlows) {

            boolean hasCondition =
                    hasConditionExpression(flow);

            if (!hasCondition) {

                if (defaultCandidate == null) {
                    defaultCandidate = flow;
                }
            }
        }

        if (defaultCandidate != null) {

            gateway.setAttribute(
                    "default",
                    defaultCandidate.getAttribute("id"));
        }
    }
}

    private List<Element> findOutgoingSequenceFlows(Document document, Element gateway) {
        List<String> outgoingIds = collectOutgoingIds(gateway);
        if (!outgoingIds.isEmpty()) {
            return findSequenceFlowsByIds(document, outgoingIds);
        }

        String gatewayId = gateway.getAttribute("id");
        List<Element> outgoingFlows = new ArrayList<>();
        NodeList sequenceFlows = document.getElementsByTagNameNS(BPMN_NAMESPACE, "sequenceFlow");
        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element sequenceFlow = (Element) sequenceFlows.item(i);
            if (gatewayId.equals(sequenceFlow.getAttribute("sourceRef"))) {
                outgoingFlows.add(sequenceFlow);
            }
        }
        return outgoingFlows;
    }

    private List<String> collectOutgoingIds(Element gateway) {
        List<String> outgoingIds = new ArrayList<>();
        NodeList childNodes = gateway.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (isBpmnElement(child, "outgoing") && hasText(child.getTextContent())) {
                outgoingIds.add(child.getTextContent().trim());
            }
        }
        return outgoingIds;
    }

    private List<Element> findSequenceFlowsByIds(Document document, List<String> outgoingIds) {
        List<Element> outgoingFlows = new ArrayList<>();
        for (String outgoingId : outgoingIds) {
            Element sequenceFlow = findSequenceFlowById(document, outgoingId);
            if (sequenceFlow != null) {
                outgoingFlows.add(sequenceFlow);
            }
        }
        return outgoingFlows;
    }

    private Element findSequenceFlowById(Document document, String id) {
        NodeList sequenceFlows = document.getElementsByTagNameNS(BPMN_NAMESPACE, "sequenceFlow");
        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element sequenceFlow = (Element) sequenceFlows.item(i);
            if (id.equals(sequenceFlow.getAttribute("id"))) {
                return sequenceFlow;
            }
        }
        return null;
    }

    private boolean hasConditionalFlow(List<Element> outgoingFlows) {
        return outgoingFlows.stream().anyMatch(this::hasConditionExpression);
    }

    private boolean hasConditionExpression(Element sequenceFlow) {
        NodeList childNodes = sequenceFlow.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (isBpmnElement(childNodes.item(i), "conditionExpression")) {
                return true;
            }
        }
        return false;
    }

    private void enrichTaskDefinitions(Document document, String bpmnTaskElementName, String zeebeTaskType) {
        NodeList tasks = document.getElementsByTagNameNS(BPMN_NAMESPACE, bpmnTaskElementName);
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            Element extensionElements = getOrCreateExtensionElements(document, task);
            if (hasZeebeTaskDefinition(extensionElements)) {
                continue;
            }

            Element taskDefinition = document.createElementNS(ZEEBE_NAMESPACE, ZEEBE_PREFIX + ":taskDefinition");
            taskDefinition.setAttribute("type", zeebeTaskType);
            extensionElements.appendChild(taskDefinition);
        }
    }

    private Element getOrCreateExtensionElements(Document document, Element bpmnElement) {
        NodeList childNodes = bpmnElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (isBpmnElement(child, "extensionElements")) {
                return (Element) child;
            }
        }

        Element extensionElements = document.createElementNS(BPMN_NAMESPACE, qualifiedName(bpmnElement, "extensionElements"));
        bpmnElement.insertBefore(extensionElements, insertionPointAfterDocumentation(bpmnElement));
        return extensionElements;
    }

    private Node insertionPointAfterDocumentation(Element bpmnElement) {
        Node insertionPoint = firstElementChild(bpmnElement);
        Node child = insertionPoint;
        while (child != null && isBpmnElement(child, "documentation")) {
            insertionPoint = child.getNextSibling();
            child = nextElementSibling(child);
        }
        return insertionPoint;
    }

    private Node firstElementChild(Element element) {
        Node child = element.getFirstChild();
        while (child != null && child.getNodeType() != Node.ELEMENT_NODE) {
            child = child.getNextSibling();
        }
        return child;
    }

    private Node nextElementSibling(Node node) {
        Node sibling = node.getNextSibling();
        while (sibling != null && sibling.getNodeType() != Node.ELEMENT_NODE) {
            sibling = sibling.getNextSibling();
        }
        return sibling;
    }

    private boolean hasZeebeTaskDefinition(Element extensionElements) {
        NodeList childNodes = extensionElements.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && ZEEBE_NAMESPACE.equals(child.getNamespaceURI())
                    && "taskDefinition".equals(child.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    private String qualifiedName(Element contextElement, String localName) {
        String prefix = contextElement.getPrefix();
        if (hasText(prefix)) {
            return prefix + ":" + localName;
        }
        return localName;
    }

    private boolean isBpmnElement(Node node, String localName) {
        return node.getNodeType() == Node.ELEMENT_NODE
                && BPMN_NAMESPACE.equals(node.getNamespaceURI())
                && localName.equals(node.getLocalName());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String write(Document document) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception ex) {
            throw new CamundaIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to write Zeebe-compatible BPMN XML", ex);
        }
    }
}