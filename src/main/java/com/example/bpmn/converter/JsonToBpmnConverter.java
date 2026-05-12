package com.example.bpmn.converter;

import com.example.bpmn.dto.BpmnRequest;
import com.example.bpmn.dto.ElementDTO;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.dto.ProcessDTO;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelException;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.ScriptTask;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JsonToBpmnConverter {

    private static final String START_EVENT_ID = "startEvent";
    private static final double START_X = 160;
    private static final double START_Y = 120;
    private static final double HORIZONTAL_GAP = 180;
    private static final double VERTICAL_GAP = 140;
    private static final double EVENT_SIZE = 36;
    private static final double GATEWAY_SIZE = 50;
    private static final double TASK_WIDTH = 100;
    private static final double TASK_HEIGHT = 80;

    public String convert(BpmnRequest request) {
        ProcessDTO processDTO = validateRequest(request);
        String processId = requireText(processDTO.getId(), "process id is required");

        BpmnModelInstance modelInstance = currentBuilder(Bpmn.createExecutableProcess(processId).startEvent(START_EVENT_ID).name("Start")).done();
        Process process = modelInstance.getModelElementById(processId);
        setName(process, processDTO.getName());

        Map<String, FlowNode> nodesById = new LinkedHashMap<>();
        StartEvent startEvent = modelInstance.getModelElementById(START_EVENT_ID);
        nodesById.put(START_EVENT_ID, startEvent);

        for (ElementDTO element : getElements(request, processDTO)) {
            String elementId = requireElementId(element);
            if (nodesById.containsKey(elementId)) {
                throw new IllegalArgumentException("Duplicate BPMN element id: " + elementId);
            }
            FlowNode node = createFlowNode(modelInstance, process, element);
            nodesById.put(node.getId(), node);
        }

        Set<String> flowIds = new HashSet<>();
        List<SequenceFlow> sequenceFlows = new ArrayList<>();
        for (FlowDTO flow : getFlows(request, processDTO)) {
            sequenceFlows.add(createSequenceFlow(modelInstance, process, nodesById, flow, flowIds));
        }

        applyCamundaModelApiLayout(modelInstance, process, nodesById, sequenceFlows);
        Bpmn.validateModel(modelInstance);
        return writeModelToString(modelInstance);
    }

    private ProcessDTO validateRequest(BpmnRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("BPMN request is required");
        }
        if (request.getProcess() == null) {
            throw new IllegalArgumentException("process is required");
        }
        return request.getProcess();
    }

    private String requireElementId(ElementDTO element) {
        if (element == null) {
            throw new IllegalArgumentException("BPMN element cannot be null");
        }
        return requireText(element.getId(), "element id is required");
    }

    private List<ElementDTO> getElements(BpmnRequest request, ProcessDTO processDTO) {
        List<ElementDTO> elements = request.getElements();
        if (elements == null || elements.isEmpty()) {
            elements = processDTO.getElements();
        }
        if (elements == null || elements.isEmpty()) {
            throw new IllegalArgumentException("At least one BPMN element is required");
        }
        return elements;
    }

    private List<FlowDTO> getFlows(BpmnRequest request, ProcessDTO processDTO) {
        List<FlowDTO> flows = request.getFlows();
        if (flows == null || flows.isEmpty()) {
            flows = processDTO.getFlows();
        }
        if (flows == null || flows.isEmpty()) {
            throw new IllegalArgumentException("At least one BPMN sequence flow is required");
        }
        return flows;
    }

    private FlowNode createFlowNode(BpmnModelInstance modelInstance, Process process, ElementDTO element) {
        if (element == null) {
            throw new IllegalArgumentException("BPMN element cannot be null");
        }

        String id = requireText(element.getId(), "element id is required");
        String type = requireText(element.getType(), "element type is required");

        return switch (type) {
            case "userTask" -> createNode(modelInstance, process, id, element.getName(), UserTask.class);
            case "serviceTask" -> createNode(modelInstance, process, id, element.getName(), ServiceTask.class);
            case "exclusiveGateway" -> createNode(modelInstance, process, id, element.getName(), ExclusiveGateway.class);
            case "endEvent" -> createNode(modelInstance, process, id, element.getName(), EndEvent.class);
            case "parallelGateway" -> createNode(modelInstance, process, id, element.getName(), ParallelGateway.class);
            case "inclusiveGateway" -> createNode(modelInstance, process, id, element.getName(), InclusiveGateway.class);
            case "scriptTask" -> createNode(modelInstance, process, id, element.getName(), ScriptTask.class);
            default -> throw new IllegalArgumentException("Unsupported BPMN element type: " + type);
        };
    }

    private <T extends FlowNode> T createNode(BpmnModelInstance modelInstance, Process process, String id, String name, Class<T> nodeType) {
        T node = modelInstance.newInstance(nodeType);
        node.setId(requireText(id, "BPMN node id is required"));
        setName(node, name);
        process.addChildElement(node);
        return node;
    }

    private SequenceFlow createSequenceFlow(BpmnModelInstance modelInstance, Process process, Map<String, FlowNode> nodesById, FlowDTO flow, Set<String> flowIds) {
        if (flow == null) {
            throw new IllegalArgumentException("BPMN flow cannot be null");
        }

        String from = requireText(flow.getFrom(), "flow from is required");
        String to = requireText(flow.getTo(), "flow to is required");
        FlowNode source = nodesById.get(from);
        FlowNode target = nodesById.get(to);

        if (source == null) {
            throw new IllegalArgumentException("Unknown source element for flow: " + from);
        }
        if (target == null) {
            throw new IllegalArgumentException("Unknown target element for flow: " + to);
        }

        String flowId = normalizeFlowId(flow.getId(), from, to, flowIds.size() + 1);
        if (!flowIds.add(flowId)) {
            throw new IllegalArgumentException("Duplicate BPMN flow id: " + flowId);
        }

        SequenceFlow sequenceFlow = modelInstance.newInstance(SequenceFlow.class);
        sequenceFlow.setId(flowId);
        setName(sequenceFlow, flow.getName());
        sequenceFlow.setSource(source);
        sequenceFlow.setTarget(target);

        String condition = trimToNull(flow.getCondition());
        if (condition != null) {
            ConditionExpression conditionExpression = modelInstance.newInstance(ConditionExpression.class);
            conditionExpression.setTextContent(condition);
            sequenceFlow.setConditionExpression(conditionExpression);
        }

        process.addChildElement(sequenceFlow);
        source.getOutgoing().add(sequenceFlow);
        target.getIncoming().add(sequenceFlow);
        return sequenceFlow;
    }

    private void applyCamundaModelApiLayout(BpmnModelInstance modelInstance, Process process, Map<String, FlowNode> nodesById, List<SequenceFlow> sequenceFlows) {
        try {
            Definitions definitions = modelInstance.getDefinitions();
            definitions.setTargetNamespace(BpmnModelConstants.BPMN20_NS);
            new ArrayList<>(definitions.getBpmDiagrams()).forEach(definitions::removeChildElement);

            BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
            diagram.setId("BPMNDiagram_" + process.getId());

            BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
            plane.setId("BPMNPlane_" + process.getId());
            plane.setBpmnElement(process);
            diagram.setBpmnPlane(plane);
            definitions.addChildElement(diagram);

            Map<String, NodeLayout> nodeLayouts = calculateNodeLayouts(nodesById, sequenceFlows);
            for (FlowNode node : nodesById.values()) {
                createBpmnShape(modelInstance, plane, node, nodeLayouts.get(node.getId()));
            }
            for (SequenceFlow sequenceFlow : sequenceFlows) {
                createBpmnEdge(modelInstance, plane, sequenceFlow, nodeLayouts);
            }
        } catch (BpmnModelException ex) {
            throw new IllegalArgumentException("Unable to generate BPMN diagram layout", ex);
        }
    }

    private Map<String, NodeLayout> calculateNodeLayouts(Map<String, FlowNode> nodesById, List<SequenceFlow> sequenceFlows) {
        Map<String, Integer> levels = new HashMap<>();
        nodesById.keySet().forEach(nodeId -> levels.put(nodeId, 0));

        for (int i = 0; i < nodesById.size(); i++) {
            boolean changed = false;
            for (SequenceFlow sequenceFlow : sequenceFlows) {
                String sourceId = sequenceFlow.getSource().getId();
                String targetId = sequenceFlow.getTarget().getId();
                int candidateLevel = levels.getOrDefault(sourceId, 0) + 1;
                if (candidateLevel > levels.getOrDefault(targetId, 0)) {
                    levels.put(targetId, candidateLevel);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        Map<Integer, Integer> rowByLevel = new HashMap<>();
        Map<String, NodeLayout> nodeLayouts = new HashMap<>();
        for (FlowNode node : nodesById.values()) {
            int level = levels.getOrDefault(node.getId(), 0);
            int row = rowByLevel.merge(level, 1, Integer::sum) - 1;
            double width = getNodeWidth(node);
            double height = getNodeHeight(node);
            double x = START_X + (level * HORIZONTAL_GAP);
            double y = START_Y + (row * VERTICAL_GAP);
            nodeLayouts.put(node.getId(), new NodeLayout(x, y, width, height));
        }
        return nodeLayouts;
    }

    private void createBpmnShape(BpmnModelInstance modelInstance, BpmnPlane plane, FlowNode node, NodeLayout layout) {
        BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
        shape.setId(node.getId() + "_di");
        shape.setBpmnElement(node);

        Bounds bounds = modelInstance.newInstance(Bounds.class);
        bounds.setX(layout.x());
        bounds.setY(layout.y());
        bounds.setWidth(layout.width());
        bounds.setHeight(layout.height());
        shape.setBounds(bounds);

        plane.addChildElement(shape);
    }

    private void createBpmnEdge(BpmnModelInstance modelInstance, BpmnPlane plane, SequenceFlow sequenceFlow, Map<String, NodeLayout> nodeLayouts) {
        NodeLayout sourceLayout = nodeLayouts.get(sequenceFlow.getSource().getId());
        NodeLayout targetLayout = nodeLayouts.get(sequenceFlow.getTarget().getId());

        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setId(sequenceFlow.getId() + "_di");
        edge.setBpmnElement(sequenceFlow);

        double sourceX = sourceLayout.right();
        double sourceY = sourceLayout.centerY();
        double targetX = targetLayout.x();
        double targetY = targetLayout.centerY();

        addWaypoint(modelInstance, edge, sourceX, sourceY);
        if (Math.abs(sourceY - targetY) > 1) {
            double middleX = sourceX + ((targetX - sourceX) / 2);
            addWaypoint(modelInstance, edge, middleX, sourceY);
            addWaypoint(modelInstance, edge, middleX, targetY);
        }
        addWaypoint(modelInstance, edge, targetX, targetY);

        plane.addChildElement(edge);
    }

    private void addWaypoint(BpmnModelInstance modelInstance, BpmnEdge edge, double x, double y) {
        Waypoint waypoint = modelInstance.newInstance(Waypoint.class);
        waypoint.setX(x);
        waypoint.setY(y);
        edge.addChildElement(waypoint);
    }

    private double getNodeWidth(FlowNode node) {
        if (node instanceof StartEvent || node instanceof EndEvent) {
            return EVENT_SIZE;
        }
        if (node instanceof ExclusiveGateway || node instanceof ParallelGateway || node instanceof InclusiveGateway) {
            return GATEWAY_SIZE;
        }
        return TASK_WIDTH;
    }

    private double getNodeHeight(FlowNode node) {
        if (node instanceof StartEvent || node instanceof EndEvent) {
            return EVENT_SIZE;
        }
        if (node instanceof ExclusiveGateway || node instanceof ParallelGateway || node instanceof InclusiveGateway) {
            return GATEWAY_SIZE;
        }
        return TASK_HEIGHT;
    }

    private AbstractFlowNodeBuilder<?, ?> currentBuilder(AbstractFlowNodeBuilder<?, ?> builder) {
        return builder;
    }

    private String normalizeFlowId(String id, String from, String to, int position) {
        String normalized = trimToNull(id);
        if (normalized != null) {
            return normalized;
        }
        return "flow_" + sanitizeIdPart(from) + "_to_" + sanitizeIdPart(to) + "_" + position;
    }

    private String sanitizeIdPart(String value) {
        return value.trim().replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private void setName(FlowNode node, String name) {
        String normalized = trimToNull(name);
        if (normalized != null) {
            node.setName(normalized);
        }
    }

    private void setName(Process process, String name) {
        String normalized = trimToNull(name);
        if (normalized != null) {
            process.setName(normalized);
        }
    }

    private void setName(SequenceFlow flow, String name) {
        String normalized = trimToNull(name);
        if (normalized != null) {
            flow.setName(normalized);
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String writeModelToString(BpmnModelInstance modelInstance) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Bpmn.writeModelToStream(outputStream, modelInstance);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private record NodeLayout(double x, double y, double width, double height) {

        double right() {
            return x + width;
        }

        double centerY() {
            return y + (height / 2);
        }
    }
}