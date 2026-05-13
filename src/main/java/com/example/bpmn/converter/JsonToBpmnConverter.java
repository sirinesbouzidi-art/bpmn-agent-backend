package com.example.bpmn.converter;

import com.example.bpmn.factory.task.TaskFactory;
import com.example.bpmn.factory.gateway.GatewayFactory;
import com.example.bpmn.factory.event.EventFactory;
import com.example.bpmn.factory.flow.FlowFactory;
import com.example.bpmn.factory.subprocess.SubProcessFactory;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import com.example.bpmn.dto.BpmnRequest;
import com.example.bpmn.dto.ElementDTO;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.dto.ProcessDTO;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelException;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JsonToBpmnConverter {

    private static final String START_EVENT_ID = "startEvent";
    private static final double START_X = 160;
    private static final double START_Y = 120;
    private static final double HORIZONTAL_GAP = 350;
    private static final double VERTICAL_GAP = 250;
    private static final double EVENT_SIZE = 36;
    private static final double GATEWAY_SIZE = 50;
    private static final double TASK_WIDTH = 100;
    private static final double TASK_HEIGHT = 80;
    private final TaskFactory taskFactory = new TaskFactory();
    private final GatewayFactory gatewayFactory = new GatewayFactory();
    private final EventFactory eventFactory = new EventFactory();
    private final FlowFactory flowFactory = new FlowFactory();
    private final SubProcessFactory subProcessFactory = new SubProcessFactory();
    private Map<String, NodeLayout> globalNodeLayouts = new HashMap<>();



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

        
        List<SequenceFlow> sequenceFlows = new ArrayList<>();

        for (FlowDTO flow : getFlows(request, processDTO)) {

        SequenceFlow sequenceFlow =
            flowFactory.createSequenceFlow(
                    modelInstance,
                    process,
                    flow,
                    nodesById
            );

        sequenceFlows.add(sequenceFlow);
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
        String type = requireText(element.getType(), "element type is required");

        return switch (type) {
            case "userTask", "serviceTask", "scriptTask", "businessRuleTask", "manualTask", "sendTask", "receiveTask" -> taskFactory.createTask(modelInstance, process, element);
            case "exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway", "complexGateway" -> gatewayFactory.createGateway(modelInstance, process, element);
            case "startEvent", "messageStartEvent", "timerStartEvent", "signalStartEvent", "conditionalStartEvent",
            "intermediateCatchEvent", "intermediateThrowEvent", "messageEvent", "timerEvent", "signalEvent", "escalationEvent", "compensationEvent", "linkEvent",
            "endEvent", "terminateEndEvent", "errorEndEvent", "messageEndEvent", "signalEndEvent", "escalationEndEvent" -> eventFactory.createEvent( modelInstance, process, element );
            case "subProcess" -> subProcessFactory.createSubProcess( modelInstance, process, element );

            default -> throw new IllegalArgumentException("Unsupported BPMN element type: " + type);
        };
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

            globalNodeLayouts = calculateNodeLayouts( nodesById, sequenceFlows);
            for (FlowNode node : nodesById.values()) {

    if (node instanceof SubProcess subProcess) {

        createSubProcessDiagram(
                modelInstance,
                plane,
                subProcess
        );

    } else {

        createBpmnShape(
                modelInstance,
                plane,
                node,
                globalNodeLayouts.get(node.getId())
        );
    }
}
            for (SequenceFlow sequenceFlow : sequenceFlows) {
                createBpmnEdge(modelInstance, plane, sequenceFlow, globalNodeLayouts);
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
    private Map<String, NodeLayout> calculateSubProcessLayouts(
        Map<String, FlowNode> nodesById,
        List<SequenceFlow> sequenceFlows,
        SubProcess subProcess
) {

    NodeLayout subLayout = globalNodeLayouts.get(subProcess.getId());
    if (subLayout == null) {

    throw new IllegalArgumentException(
            "No layout found for subprocess: "
                    + subProcess.getId()
    );
    }

    double internalStartX = subLayout.x() + 40;
    double internalStartY = subLayout.y() + 40;

    Map<String, Integer> levels =
            new HashMap<>();

    nodesById.keySet().forEach(
            nodeId -> levels.put(nodeId, 0)
    );

    for (int i = 0; i < nodesById.size(); i++) {

        boolean changed = false;

        for (SequenceFlow flow : sequenceFlows) {

            String sourceId =
                    flow.getSource().getId();

            String targetId =
                    flow.getTarget().getId();

            int candidateLevel =
                    levels.getOrDefault(sourceId, 0) + 1;

            if (candidateLevel >
                    levels.getOrDefault(targetId, 0)) {

                levels.put(targetId, candidateLevel);

                changed = true;
            }
        }

        if (!changed) {
            break;
        }
    }

    Map<Integer, Integer> rowByLevel =
            new HashMap<>();

    Map<String, NodeLayout> layouts =
            new HashMap<>();

    for (FlowNode node : nodesById.values()) {

        int level =
                levels.getOrDefault(node.getId(), 0);

        int row =
                rowByLevel.merge(level, 1, Integer::sum) - 1;

        double width = getNodeWidth(node);
        double height = getNodeHeight(node);

        double x =
                internalStartX + (level * HORIZONTAL_GAP);

        double y =
                internalStartY + (row * VERTICAL_GAP);

        layouts.put(
                node.getId(),
                new NodeLayout(
                        x,
                        y,
                        width,
                        height
                )
        );
    }

    return layouts;
   }

    private void createBpmnShape(
        BpmnModelInstance modelInstance,
        BpmnPlane plane,
        FlowNode node,
        NodeLayout layout
) {

    BpmnShape shape =
            modelInstance.newInstance(BpmnShape.class);

    shape.setId(node.getId() + "_di");

    shape.setBpmnElement(node);

    if (node instanceof SubProcess) {

    shape.setExpanded(false);

    shape.setHorizontal(true);
}

    Bounds bounds =
            modelInstance.newInstance(Bounds.class);

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

    private void createSubProcessDiagram(
        BpmnModelInstance modelInstance,
        BpmnPlane plane,
        SubProcess subProcess
) {

    Map<String, FlowNode> subNodes =
            new LinkedHashMap<>();

    List<SequenceFlow> subFlows =
            new ArrayList<>();

    for (FlowElement element : subProcess.getFlowElements()) {

        if (element instanceof FlowNode node) {
            subNodes.put(node.getId(), node);
        }

        if (element instanceof SequenceFlow flow) {
            subFlows.add(flow);
        }
    }

    Map<String, NodeLayout> subLayouts =
        calculateSubProcessLayouts(
            subNodes,
            subFlows,
            subProcess
        );

        resizeSubProcessShape(
            subProcess,
            subLayouts
        );

    globalNodeLayouts.putAll(subLayouts);
    createBpmnShape(
        modelInstance,
        plane,
        subProcess,
        globalNodeLayouts.get(subProcess.getId())
);

    for (FlowNode node : subNodes.values()) {

        createBpmnShape(
                modelInstance,
                plane,
                node,
                subLayouts.get(node.getId())
        );
        if (node instanceof SubProcess nestedSubProcess) {

          createSubProcessDiagram(
            modelInstance,
            plane,
            nestedSubProcess
        );
}
    }

    for (SequenceFlow flow : subFlows) {

        createBpmnEdge(
                modelInstance,
                plane,
                flow,
                subLayouts
        );
    }
    }
    private void resizeSubProcessShape(
        SubProcess subProcess,
        Map<String, NodeLayout> subLayouts
) {

    NodeLayout currentLayout =
            globalNodeLayouts.get(subProcess.getId());

    if (currentLayout == null) {
        return;
    }

    double maxRight = 0;
    double maxBottom = 0;

    for (NodeLayout layout : subLayouts.values()) {

        maxRight = Math.max(
                maxRight,
                layout.right()
        );

        maxBottom = Math.max(
                maxBottom,
                layout.y() + layout.height()
        );
    }

    double newWidth =
            (maxRight - currentLayout.x()) + 100;

    double newHeight =
            (maxBottom - currentLayout.y()) + 100;

    globalNodeLayouts.put(
            subProcess.getId(),
            new NodeLayout(
                    currentLayout.x(),
                    currentLayout.y(),
                    Math.max(newWidth, 400),
                    Math.max(newHeight, 250)
            )
    );
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
        if (node instanceof SubProcess) {
           return 400;
        }
        if (node instanceof ExclusiveGateway || node instanceof ParallelGateway || node instanceof InclusiveGateway || node instanceof org.camunda.bpm.model.bpmn.instance.EventBasedGateway || node instanceof org.camunda.bpm.model.bpmn.instance.ComplexGateway) {
           return GATEWAY_SIZE;
        }
        return TASK_WIDTH;
    }

    private double getNodeHeight(FlowNode node) {
        if (node instanceof StartEvent || node instanceof EndEvent) {
            return EVENT_SIZE;
        }
        if (node instanceof SubProcess) {
            return 250;
        }
        if (node instanceof ExclusiveGateway || node instanceof ParallelGateway || node instanceof InclusiveGateway || node instanceof org.camunda.bpm.model.bpmn.instance.EventBasedGateway || node instanceof org.camunda.bpm.model.bpmn.instance.ComplexGateway) {
            return GATEWAY_SIZE;
        }
        return TASK_HEIGHT;
    }

    private AbstractFlowNodeBuilder<?, ?> currentBuilder(AbstractFlowNodeBuilder<?, ?> builder) {
        return builder;
    }

    private void setName(Process process, String name) {
        String normalized = trimToNull(name);
        if (normalized != null) {
            process.setName(normalized);
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