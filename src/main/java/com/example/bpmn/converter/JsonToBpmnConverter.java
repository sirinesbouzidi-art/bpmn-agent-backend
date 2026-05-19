package com.example.bpmn.converter;

import com.example.bpmn.dto.BpmnRequest;
import com.example.bpmn.dto.ElementDTO;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.dto.ProcessDTO;
import com.example.bpmn.factory.event.EventFactory;
import com.example.bpmn.factory.flow.FlowFactory;
import com.example.bpmn.factory.gateway.GatewayFactory;
import com.example.bpmn.factory.subprocess.SubProcessFactory;
import com.example.bpmn.factory.task.TaskFactory;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelException;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.ComplexGateway;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.springframework.stereotype.Component;
import com.example.bpmn.factory.subprocess.CallActivityFactory;
import com.example.bpmn.factory.subprocess.EventSubProcessFactory;
import com.example.bpmn.factory.transaction.TransactionFactory;
import org.camunda.bpm.model.bpmn.instance.Transaction;
import com.example.bpmn.factory.subprocess.AdHocSubProcessFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JsonToBpmnConverter {


    private static final double START_X = 160;
    private static final double START_Y = 220;
    private static final double HORIZONTAL_GAP = 260;
    private static final double MIN_VERTICAL_GAP = 150;
    private static final double LANE_PADDING = 70;
    private static final double EVENT_SIZE = 36;
    private static final double GATEWAY_SIZE = 50;
    private static final double TASK_WIDTH = 120;
    private static final double TASK_HEIGHT = 80;
    private static final double SUBPROCESS_COLLAPSED_WIDTH = 180;
    private static final double SUBPROCESS_COLLAPSED_HEIGHT = 110;
    private static final double ADHOC_DEFAULT_WIDTH  = 500;
    private static final double ADHOC_DEFAULT_HEIGHT = 320;
    private static final double ORTHOGONAL_EDGE_OFFSET = 30;
    private static final Pattern NON_ID_CHARACTER = Pattern.compile("[^A-Za-z0-9_.-]");

    private final TaskFactory taskFactory = new TaskFactory();
    private final GatewayFactory gatewayFactory = new GatewayFactory();
    private final EventFactory eventFactory = new EventFactory();
    private final FlowFactory flowFactory = new FlowFactory();
    private final SubProcessFactory subProcessFactory = new SubProcessFactory();
    private final CallActivityFactory callActivityFactory = new CallActivityFactory();
    private final EventSubProcessFactory eventSubProcessFactory = new EventSubProcessFactory();
    private final TransactionFactory transactionFactory = new TransactionFactory();
    private final AdHocSubProcessFactory adHocSubProcessFactory = new AdHocSubProcessFactory();
    private final Map<String, NodeLayout> globalNodeLayouts = new HashMap<>();
    private final Set<String> adHocSubProcessIds = new HashSet<>();

    // =====================================================
    // ENTRY POINT
    // =====================================================

    public String convert(BpmnRequest request) {
        ProcessDTO processDTO = validateRequest(request);
        String processId = requireText(processDTO.getId(), "process id is required");

        BpmnModelInstance modelInstance = Bpmn.createEmptyModel();

        Definitions definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace("http://camunda.org/examples");
        modelInstance.setDefinitions(definitions);

        Process process = modelInstance.newInstance(Process.class);
        process.setId(processId);
        process.setExecutable(true);

        setName(process, processDTO.getName());

        definitions.addChildElement(process);

        Map<String, FlowNode> nodesById = new LinkedHashMap<>();

        List<ElementDTO> elements = getElements(request, processDTO);

        // Pre-collect ALL adHoc IDs (top-level + nested) before any node is created.
        // This ensures adHocSubProcessIds is complete before layout and shape creation,
        // so nested ad-hoc subprocesses are correctly identified at every depth.
        adHocSubProcessIds.clear();
        collectAdHocIds(elements);

        for (ElementDTO element : elements) {
            String elementId = requireElementId(element);
            if (nodesById.containsKey(elementId)) {
                throw new IllegalArgumentException("Duplicate BPMN element id: " + elementId);
            }
            FlowNode node = createFlowNode(modelInstance, process, element);
            nodesById.put(node.getId(), node);
        }

        List<SequenceFlow> sequenceFlows = new ArrayList<>();
        for (FlowDTO flow : getFlows(request, processDTO)) {
            sequenceFlows.add(flowFactory.createSequenceFlow(modelInstance, process, flow, nodesById));
        }

        applyCamundaModelApiLayout(modelInstance, process, nodesById, sequenceFlows);
        Bpmn.validateModel(modelInstance);
        return writeModelToString(modelInstance);
    }

    // =====================================================
    // VALIDATION
    // =====================================================

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
        if (flows == null) {
           return List.of();
       }
        return flows;
    }

    // =====================================================
    // NODE CREATION
    // =====================================================

    private FlowNode createFlowNode(BpmnModelInstance modelInstance, Process process, ElementDTO element) {
        if (element == null) {
            throw new IllegalArgumentException("BPMN element cannot be null");
        }
        String type = requireText(element.getType(), "element type is required");

        return switch (type) {
            case "userTask", "serviceTask", "scriptTask", "businessRuleTask",
                 "manualTask", "sendTask", "receiveTask"
                    -> taskFactory.createTask(modelInstance, process, element);

            case "exclusiveGateway", "parallelGateway", "inclusiveGateway",
                 "eventBasedGateway", "complexGateway"
                    -> gatewayFactory.createGateway(modelInstance, process, element);

            case "startEvent", "messageStartEvent", "timerStartEvent", "signalStartEvent",
                 "conditionalStartEvent", "intermediateCatchEvent", "intermediateThrowEvent",
                 "messageEvent", "timerEvent", "signalEvent", "escalationEvent",
                 "compensationEvent", "linkEvent", "endEvent", "terminateEndEvent",
                 "errorEndEvent", "messageEndEvent", "signalEndEvent", "escalationEndEvent"
                    -> eventFactory.createEvent(modelInstance, process, element);

            case "subProcess"
                    -> subProcessFactory.createSubProcess(modelInstance, process, element);

            case "callActivity"
                    -> callActivityFactory.createCallActivity(modelInstance, process, element);

            case "eventSubProcess"
                    -> eventSubProcessFactory.createEventSubProcess(modelInstance, process, element);

            case "transaction"
                    -> transactionFactory.createTransaction(modelInstance, process, element);

            // No manual adHocSubProcessIds.add() here —
            // collectAdHocIds() already handled all levels before this runs.
            case "adHocSubProcess"
                    -> adHocSubProcessFactory.createAdHocSubProcess(modelInstance, process, element);

            default -> throw new IllegalArgumentException("Unsupported BPMN element type: " + type);
        };
    }

    // =====================================================
    // ADHOC ID PRE-COLLECTION
    // =====================================================

    /**
     * Recursively collects all element IDs whose type is "adHocSubProcess"
     * from the full element tree (top-level and any depth of nesting).
     * Must be called once before model construction so adHocSubProcessIds
     * is complete when layout, shape, and diagram generation run.
     */
    private void collectAdHocIds(List<ElementDTO> elements) {
        if (elements == null) {
            return;
        }
        for (ElementDTO element : elements) {
            if ("adHocSubProcess".equals(element.getType())) {
                adHocSubProcessIds.add(element.getId());
            }
            collectAdHocIds(element.getElements());
        }
    }

    // =====================================================
    // LAYOUT ENTRY
    // =====================================================

    private void applyCamundaModelApiLayout(
            BpmnModelInstance modelInstance,
            Process process,
            Map<String, FlowNode> nodesById,
            List<SequenceFlow> sequenceFlows
    ) {
        try {
            Definitions definitions = modelInstance.getDefinitions();
            definitions.setTargetNamespace(BpmnModelConstants.BPMN20_NS);
            new ArrayList<>(definitions.getBpmDiagrams()).forEach(definitions::removeChildElement);

            Set<String> usedDiIds = new HashSet<>();
            BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
            diagram.setId(stableDiId("BPMNDiagram", process.getId(), modelInstance, usedDiIds));
            BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
            plane.setId(stableDiId("BPMNPlane", process.getId(), modelInstance, usedDiIds));
            plane.setBpmnElement(process);
            diagram.setBpmnPlane(plane);
            definitions.addChildElement(diagram);

            Map<String, NodeLayout> nodeLayouts = calculateNodeLayouts(nodesById, sequenceFlows, START_X, START_Y);
            globalNodeLayouts.clear();
            globalNodeLayouts.putAll(nodeLayouts);

            for (FlowNode node : nodesById.values()) {
                createBpmnShape(modelInstance, plane, node, nodeLayouts.get(node.getId()), usedDiIds);
            }
            for (SequenceFlow sequenceFlow : sequenceFlows) {
                createBpmnEdge(modelInstance, plane, sequenceFlow, nodeLayouts, usedDiIds);
            }
            createCollapsedSubProcessDiagrams( modelInstance, definitions, nodesById.values(), usedDiIds, nodeLayouts );
        } catch (BpmnModelException ex) {
            throw new IllegalArgumentException("Unable to generate BPMN diagram layout", ex);
        }
    }

    // =====================================================
    // SUBPROCESS / TRANSACTION DIAGRAM GENERATION
    // =====================================================

    private void createCollapsedSubProcessDiagrams(
        BpmnModelInstance modelInstance,
        Definitions definitions,
        Collection<FlowNode> nodes,
        Set<String> usedDiIds,
        Map<String, NodeLayout> parentLayouts
  ){
        for (FlowNode node : nodes) {
            if (node instanceof SubProcess subProcess) {
                createSubProcessDiagram( modelInstance, definitions, subProcess, usedDiIds, parentLayouts);
            } else if (node instanceof Transaction transaction) {
                createTransactionDiagram(modelInstance, definitions, transaction, usedDiIds);
            }
        }
    }

    private void createSubProcessDiagram(
        BpmnModelInstance modelInstance,
        Definitions definitions,
        SubProcess subProcess,
        Set<String> usedDiIds,
        Map<String, NodeLayout> parentLayouts
) {
        Map<String, FlowNode> subNodes = new LinkedHashMap<>();
        List<SequenceFlow> subFlows = new ArrayList<>();

        for (FlowElement element : subProcess.getFlowElements()) {
            if (element instanceof FlowNode node) {
                subNodes.put(node.getId(), node);
            }
            if (element instanceof SequenceFlow flow) {
                subFlows.add(flow);
            }
        }
        if (subNodes.isEmpty()) {
            return;
        }

        BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
        diagram.setId(stableDiId("BPMNDiagram", subProcess.getId(), modelInstance, usedDiIds));
        BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
        plane.setId(stableDiId("BPMNPlane", subProcess.getId(), modelInstance, usedDiIds));
        plane.setBpmnElement(subProcess);
        diagram.setBpmnPlane(plane);
        definitions.addChildElement(diagram);

        Map<String, NodeLayout> subLayouts;

        if (adHocSubProcessIds.contains(subProcess.getId())) {
            // Pass parentLayout explicitly — globalNodeLayouts is populated for top-level
            // nodes at this point; nested ad-hoc nodes are handled by the recursion below.
            NodeLayout parentLayout = parentLayouts.get(subProcess.getId());
            subLayouts = calculateAdHocLayouts(subNodes, subProcess, parentLayout);

            NodeLayout resized = resizeAdHocSubProcess( parentLayout, subLayouts );

            globalNodeLayouts.put(subProcess.getId(), resized);

            // Update the already-created BpmnShape bounds to match resized layout
            BpmnShape subShape = (BpmnShape) modelInstance.getModelElementById(
                    subProcess.getId() + "_di");
            if (subShape != null) {
                Bounds bounds = subShape.getBounds();
                bounds.setWidth(resized.width());
                bounds.setHeight(resized.height());
            }
        } else {
            subLayouts = calculateNodeLayouts(subNodes, subFlows, START_X, START_Y);
        }

        for (FlowNode node : subNodes.values()) {
            createBpmnShape(modelInstance, plane, node, subLayouts.get(node.getId()), usedDiIds);
        }
        for (SequenceFlow flow : subFlows) {
            createBpmnEdge(modelInstance, plane, flow, subLayouts, usedDiIds);
        }
        // Recurse — handles nested SubProcess / Transaction / AdHoc at any depth
        createCollapsedSubProcessDiagrams(
        modelInstance,
        definitions,
        subNodes.values(),
        usedDiIds,
        subLayouts
);
    }

    private void createTransactionDiagram(
            BpmnModelInstance modelInstance,
            Definitions definitions,
            Transaction transaction,
            Set<String> usedDiIds
    ) {
        Map<String, FlowNode> subNodes = new LinkedHashMap<>();
        List<SequenceFlow> subFlows = new ArrayList<>();

        for (FlowElement element : transaction.getFlowElements()) {
            if (element instanceof FlowNode node) {
                subNodes.put(node.getId(), node);
            }
            if (element instanceof SequenceFlow flow) {
                subFlows.add(flow);
            }
        }
        if (subNodes.isEmpty()) {
            return;
        }

        BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
        diagram.setId(stableDiId("BPMNDiagram", transaction.getId(), modelInstance, usedDiIds));
        BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
        plane.setId(stableDiId("BPMNPlane", transaction.getId(), modelInstance, usedDiIds));
        plane.setBpmnElement(transaction);
        diagram.setBpmnPlane(plane);
        definitions.addChildElement(diagram);

        Map<String, NodeLayout> subLayouts = calculateNodeLayouts(subNodes, subFlows, START_X, START_Y);
        for (FlowNode node : subNodes.values()) {
            createBpmnShape(modelInstance, plane, node, subLayouts.get(node.getId()), usedDiIds);
        }
        for (SequenceFlow flow : subFlows) {
            createBpmnEdge(modelInstance, plane, flow, subLayouts, usedDiIds);
        }
        // Recurse for any nested SubProcess or Transaction inside this Transaction
        createCollapsedSubProcessDiagrams( modelInstance, definitions, subNodes.values(), usedDiIds, subLayouts);
    }

    // =====================================================
    // LAYOUT CALCULATION
    // =====================================================

    private Map<String, NodeLayout> calculateNodeLayouts(
            Map<String, FlowNode> nodesById,
            List<SequenceFlow> sequenceFlows,
            double startX,
            double centerY
    ) {
        GraphIndex graph = buildGraphIndex(nodesById, sequenceFlows);
        Map<String, Integer> levels = calculateLevels(nodesById.keySet(), graph);
        Map<Integer, List<FlowNode>> nodesByLevel = groupNodesByLevel(nodesById.values(), levels);
        orderLevelsForReadableEdges(nodesByLevel, graph);

        Map<String, NodeLayout> nodeLayouts = new HashMap<>();
        for (Map.Entry<Integer, List<FlowNode>> entry : nodesByLevel.entrySet()) {
            int level = entry.getKey();
            List<FlowNode> levelNodes = entry.getValue();
            double verticalGap = verticalGapFor(levelNodes);
            double totalHeight = totalLevelHeight(levelNodes, verticalGap);
            double y = Math.max(START_Y, centerY - (totalHeight / 2));

            for (FlowNode node : levelNodes) {
                double width = getNodeWidth(node);
                double height = getNodeHeight(node);
                nodeLayouts.put(node.getId(), new NodeLayout(startX + (level * HORIZONTAL_GAP), y, width, height));
                y += height + verticalGap;
            }
        }
        return balanceGatewayBranches(nodeLayouts, nodesByLevel, graph);
    }

    /**
     * Lays out children of an AdHocSubProcess in a 2-column grid relative to
     * the parent container's already-computed position.
     *
     * parentLayout is passed explicitly instead of being read from globalNodeLayouts
     * to handle nested ad-hoc subprocesses whose parent may not yet be in that map.
     */
    private Map<String, NodeLayout> calculateAdHocLayouts(
            Map<String, FlowNode> nodesById,
            SubProcess subProcess,
            NodeLayout parentLayout
    ) {
        if (parentLayout == null) {
            throw new IllegalArgumentException(
                    "Missing layout for adhoc subprocess: " + subProcess.getId());
        }

        double startX = parentLayout.x() + 60;
        double startY = parentLayout.y() + 90;

        Map<String, NodeLayout> layouts = new HashMap<>();
        int index = 0;
        for (FlowNode node : nodesById.values()) {
            int column = index % 2;
            int row    = index / 2;
            double x   = startX + (column * 260);
            double y   = startY + (row    * 180);
            layouts.put(node.getId(),
                    new NodeLayout(x, y, getNodeWidth(node), getNodeHeight(node)));
            index++;
        }
        return layouts;
    }

    private GraphIndex buildGraphIndex(Map<String, FlowNode> nodesById, List<SequenceFlow> sequenceFlows) {
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        nodesById.keySet().forEach(nodeId -> {
            outgoing.put(nodeId, new ArrayList<>());
            incoming.put(nodeId, new ArrayList<>());
        });

        for (SequenceFlow sequenceFlow : sequenceFlows) {
            String sourceId = sequenceFlow.getSource().getId();
            String targetId = sequenceFlow.getTarget().getId();
            if (nodesById.containsKey(sourceId) && nodesById.containsKey(targetId)) {
                outgoing.get(sourceId).add(targetId);
                incoming.get(targetId).add(sourceId);
            }
        }
        return new GraphIndex(outgoing, incoming);
    }

    private Map<String, Integer> calculateLevels(Collection<String> nodeIds, GraphIndex graph) {
        Map<String, Integer> levels = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Queue<String> ready = new ArrayDeque<>();

        for (String nodeId : nodeIds) {
            levels.put(nodeId, 0);
            int incomingCount = graph.incoming().getOrDefault(nodeId, List.of()).size();
            indegree.put(nodeId, incomingCount);
            if (incomingCount == 0) {
                ready.add(nodeId);
            }
        }

        while (!ready.isEmpty()) {
            String sourceId = ready.remove();
            for (String targetId : graph.outgoing().getOrDefault(sourceId, List.of())) {
                levels.put(targetId, Math.max(
                        levels.getOrDefault(targetId, 0),
                        levels.getOrDefault(sourceId, 0) + 1));
                int newIndegree = indegree.merge(targetId, -1, Integer::sum);
                if (newIndegree == 0) {
                    ready.add(targetId);
                }
            }
        }

        for (int i = 0; i < nodeIds.size(); i++) {
            boolean changed = false;
            for (Map.Entry<String, List<String>> entry : graph.outgoing().entrySet()) {
                for (String targetId : entry.getValue()) {
                    int candidateLevel = levels.getOrDefault(entry.getKey(), 0) + 1;
                    if (candidateLevel > levels.getOrDefault(targetId, 0)
                            && candidateLevel <= nodeIds.size()) {
                        levels.put(targetId, candidateLevel);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        return levels;
    }

    private Map<Integer, List<FlowNode>> groupNodesByLevel(
            Collection<FlowNode> nodes, Map<String, Integer> levels) {
        Map<Integer, List<FlowNode>> nodesByLevel = new LinkedHashMap<>();
        nodes.stream()
                .sorted(Comparator.comparingInt(node -> levels.getOrDefault(node.getId(), 0)))
                .forEach(node -> nodesByLevel.computeIfAbsent(
                        levels.getOrDefault(node.getId(), 0),
                        ignored -> new ArrayList<>()).add(node));
        return nodesByLevel;
    }

    private void orderLevelsForReadableEdges(
            Map<Integer, List<FlowNode>> nodesByLevel, GraphIndex graph) {
        Map<String, Integer> order = new HashMap<>();
        nodesByLevel.values().forEach(levelNodes -> {
            for (int i = 0; i < levelNodes.size(); i++) {
                order.put(levelNodes.get(i).getId(), i);
            }
        });
        nodesByLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .skip(1)
                .forEach(entry -> entry.getValue().sort(Comparator.comparingDouble(
                        node -> averageOrder(
                                graph.incoming().getOrDefault(node.getId(), List.of()), order))));
    }

    private double averageOrder(List<String> relatedNodeIds, Map<String, Integer> order) {
        OptionalDouble average = relatedNodeIds.stream()
                .filter(order::containsKey)
                .mapToInt(order::get)
                .average();
        return average.orElse(0);
    }

    private Map<String, NodeLayout> balanceGatewayBranches(
            Map<String, NodeLayout> nodeLayouts,
            Map<Integer, List<FlowNode>> nodesByLevel,
            GraphIndex graph
    ) {
        Map<String, NodeLayout> balancedLayouts = new HashMap<>(nodeLayouts);
        Map<String, Integer> levelByNodeId = new HashMap<>();
        nodesByLevel.forEach((level, nodes) ->
                nodes.forEach(node -> levelByNodeId.put(node.getId(), level)));

        for (Map.Entry<Integer, List<FlowNode>> entry : nodesByLevel.entrySet()) {
            for (FlowNode node : entry.getValue()) {
                List<String> outgoing = graph.outgoing().getOrDefault(node.getId(), List.of());
                if (!isGateway(node) || outgoing.size() < 2) {
                    continue;
                }
                List<String> nextLevelTargets = outgoing.stream()
                        .filter(targetId ->
                                levelByNodeId.getOrDefault(targetId, -1) == entry.getKey() + 1)
                        .filter(targetId ->
                                graph.incoming().getOrDefault(targetId, List.of()).size() == 1)
                        .toList();
                if (nextLevelTargets.size() < 2) {
                    continue;
                }

                double gatewayCenterY = balancedLayouts.get(node.getId()).centerY();
                double branchGap = Math.max(MIN_VERTICAL_GAP,
                        verticalGapForLayouts(nextLevelTargets, balancedLayouts));
                double firstCenterY = gatewayCenterY
                        - ((nextLevelTargets.size() - 1) * branchGap / 2);
                for (int i = 0; i < nextLevelTargets.size(); i++) {
                    String targetId = nextLevelTargets.get(i);
                    NodeLayout current = balancedLayouts.get(targetId);
                    double newY = firstCenterY + (i * branchGap) - (current.height() / 2);
                    balancedLayouts.put(targetId, current.withY(Math.max(START_Y, newY)));
                }
            }
        }
        normalizeLevelSpacing(balancedLayouts, nodesByLevel);
        return balancedLayouts;
    }

    private void normalizeLevelSpacing(
            Map<String, NodeLayout> nodeLayouts,
            Map<Integer, List<FlowNode>> nodesByLevel) {
        for (List<FlowNode> levelNodes : nodesByLevel.values()) {
            levelNodes.sort(Comparator.comparingDouble(
                    node -> nodeLayouts.get(node.getId()).centerY()));
            double nextY = START_Y;
            for (FlowNode node : levelNodes) {
                NodeLayout current = nodeLayouts.get(node.getId());
                if (current.y() < nextY) {
                    current = current.withY(nextY);
                    nodeLayouts.put(node.getId(), current);
                }
                nextY = current.y() + current.height() + verticalGapFor(levelNodes);
            }
        }
    }

    private double verticalGapFor(List<FlowNode> nodes) {
        boolean hasGateway = nodes.stream().anyMatch(this::isGateway);
        return Math.max(MIN_VERTICAL_GAP, (hasGateway ? 190 : 160) + (nodes.size() * 8));
    }

    private double verticalGapForLayouts(
            List<String> nodeIds, Map<String, NodeLayout> nodeLayouts) {
        double maxHeight = nodeIds.stream()
                .map(nodeLayouts::get)
                .mapToDouble(NodeLayout::height)
                .max()
                .orElse(TASK_HEIGHT);
        return maxHeight + LANE_PADDING;
    }

    private double totalLevelHeight(List<FlowNode> levelNodes, double verticalGap) {
        double nodeHeight = levelNodes.stream().mapToDouble(this::getNodeHeight).sum();
        return nodeHeight + (Math.max(0, levelNodes.size() - 1) * verticalGap);
    }

    // =====================================================
    // SHAPE / EDGE CREATION
    // =====================================================

    private void createBpmnShape(
            BpmnModelInstance modelInstance,
            BpmnPlane plane,
            FlowNode node,
            NodeLayout layout,
            Set<String> usedDiIds
    ) {
        if (layout == null) {
            throw new IllegalArgumentException("No diagram layout found for node: " + node.getId());
        }

        BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
        shape.setId(stableDiId("BPMNShape", node.getId(), modelInstance, usedDiIds));
        shape.setBpmnElement(node);

        if (node instanceof SubProcess subProcess) {
            // adHocSubProcessIds is fully populated (top-level + nested) via collectAdHocIds(),
            // so this check is correct at any nesting depth.
            shape.setExpanded(adHocSubProcessIds.contains(subProcess.getId()));
            shape.setHorizontal(true);
        }

        if (node instanceof Transaction) {
            shape.setExpanded(false);
            shape.setHorizontal(true);
        }

        Bounds bounds = modelInstance.newInstance(Bounds.class);
        bounds.setX(layout.x());
        bounds.setY(layout.y());
        bounds.setWidth(layout.width());
        bounds.setHeight(layout.height());
        shape.setBounds(bounds);
        plane.addChildElement(shape);
    }

    private void createBpmnEdge(
            BpmnModelInstance modelInstance,
            BpmnPlane plane,
            SequenceFlow sequenceFlow,
            Map<String, NodeLayout> nodeLayouts,
            Set<String> usedDiIds
    ) {
        NodeLayout sourceLayout = nodeLayouts.get(sequenceFlow.getSource().getId());
        NodeLayout targetLayout = nodeLayouts.get(sequenceFlow.getTarget().getId());
        if (sourceLayout == null || targetLayout == null) {
            throw new IllegalArgumentException( "No diagram layout found for sequence flow: " + sequenceFlow.getId());
        }

        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setId(stableDiId("BPMNEdge", sequenceFlow.getId(), modelInstance, usedDiIds));
        edge.setBpmnElement(sequenceFlow);

        if (targetLayout.x() >= sourceLayout.right()) {
            addLeftToRightWaypoints(modelInstance, edge, sourceLayout, targetLayout);
        } else {
            addLoopWaypoints(modelInstance, edge, sourceLayout, targetLayout);
        }

        plane.addChildElement(edge);
    }

    private void addLeftToRightWaypoints( BpmnModelInstance modelInstance, BpmnEdge edge, NodeLayout sourceLayout, NodeLayout targetLayout) {
        double sourceX = sourceLayout.right();
        double sourceY = sourceLayout.centerY();
        double targetX = targetLayout.x();
        double targetY = targetLayout.centerY();
        double middleX = sourceX + ((targetX - sourceX) / 2);

        addWaypoint(modelInstance, edge, sourceX, sourceY);
        addWaypoint(modelInstance, edge, middleX, sourceY);
        addWaypoint(modelInstance, edge, middleX, targetY);
        addWaypoint(modelInstance, edge, targetX, targetY);
    }

    private void addLoopWaypoints(
            BpmnModelInstance modelInstance, BpmnEdge edge,
            NodeLayout sourceLayout, NodeLayout targetLayout) {
        double sourceX = sourceLayout.x() + (sourceLayout.width() / 2);
        double sourceY = sourceLayout.y() + sourceLayout.height();
        double targetX = targetLayout.x() + (targetLayout.width() / 2);
        double targetY = targetLayout.y() + targetLayout.height();
        double loopY   = Math.max(sourceY, targetY) + ORTHOGONAL_EDGE_OFFSET;

        addWaypoint(modelInstance, edge, sourceX, sourceY);
        addWaypoint(modelInstance, edge, sourceX, loopY);
        addWaypoint(modelInstance, edge, targetX, loopY);
        addWaypoint(modelInstance, edge, targetX, targetY);
    }

    private void addWaypoint(BpmnModelInstance modelInstance, BpmnEdge edge, double x, double y) {
        Waypoint waypoint = modelInstance.newInstance(Waypoint.class);
        waypoint.setX(x);
        waypoint.setY(y);
        edge.addChildElement(waypoint);
    }

    // =====================================================
    // NODE SIZE HELPERS
    // =====================================================

    private double getNodeWidth(FlowNode node) {
        if (node instanceof StartEvent || node instanceof EndEvent) {
            return EVENT_SIZE;
        }
        if (node instanceof SubProcess subProcess) {
            if (adHocSubProcessIds.contains(subProcess.getId())) {
                NodeLayout layout = globalNodeLayouts.get(subProcess.getId());
                // FIX: was hardcoded magic number 500 — use named constant
                return layout != null ? layout.width() : ADHOC_DEFAULT_WIDTH;
            }
            return SUBPROCESS_COLLAPSED_WIDTH;
        }
        if (node instanceof Transaction) {
            return SUBPROCESS_COLLAPSED_WIDTH;
        }
        if (isGateway(node)) {
            return GATEWAY_SIZE;
        }
        return TASK_WIDTH;
    }

    private double getNodeHeight(FlowNode node) {
        if (node instanceof StartEvent || node instanceof EndEvent) {
            return EVENT_SIZE;
        }
        if (node instanceof SubProcess subProcess) {
            if (adHocSubProcessIds.contains(subProcess.getId())) {
                NodeLayout layout = globalNodeLayouts.get(subProcess.getId());
                // FIX: was hardcoded magic number 320 — use named constant
                return layout != null ? layout.height() : ADHOC_DEFAULT_HEIGHT;
            }
            return SUBPROCESS_COLLAPSED_HEIGHT;
        }
        if (node instanceof Transaction) {
            return SUBPROCESS_COLLAPSED_HEIGHT;
        }
        if (isGateway(node)) {
            return GATEWAY_SIZE;
        }
        return TASK_HEIGHT;
    }

    private boolean isGateway(FlowNode node) {
        return node instanceof ExclusiveGateway
                || node instanceof ParallelGateway
                || node instanceof InclusiveGateway
                || node instanceof EventBasedGateway
                || node instanceof ComplexGateway;
    }

    // =====================================================
    // ADHOC RESIZE
    // =====================================================

    /**
     * Computes the tightest bounding box around all internal node layouts
     * and returns a resized NodeLayout for the AdHocSubProcess container.
     */
    private NodeLayout resizeAdHocSubProcess(
            NodeLayout currentLayout,
            Map<String, NodeLayout> internalLayouts
    ) {
        double minX =  Double.MAX_VALUE;
        double minY =  Double.MAX_VALUE;
        // FIX: was Double.MIN_VALUE which is a tiny positive number (~5e-324), not negative infinity.
        //      Use -Double.MAX_VALUE so the first real coordinate always wins the max comparison.
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (NodeLayout layout : internalLayouts.values()) {
            minX = Math.min(minX, layout.x());
            minY = Math.min(minY, layout.y());
            maxX = Math.max(maxX, layout.x() + layout.width());
            maxY = Math.max(maxY, layout.y() + layout.height());
        }

        double padding = 160;
        double width   = (maxX - minX) + padding;
        double height  = (maxY - minY) + padding;

        return new NodeLayout(
                currentLayout.x(),
                currentLayout.y(),
                Math.max(width, 650),
                Math.max(height, 420)
        );
    }

    // =====================================================
    // DI ID GENERATION
    // =====================================================

    private String stableDiId(
            String prefix,
            String bpmnElementId,
            BpmnModelInstance modelInstance,
            Set<String> usedIds
    ) {
        String normalizedId = NON_ID_CHARACTER
                .matcher(prefix + "_" + bpmnElementId + "_di")
                .replaceAll("_");
        if (!normalizedId.matches("[A-Za-z_].*")) {
            normalizedId = "di_" + normalizedId;
        }
        String candidate = normalizedId;
        int suffix = 2;
        while (usedIds.contains(candidate)
                || modelInstance.getModelElementById(candidate) != null) {
            candidate = normalizedId + "_" + suffix;
            suffix++;
        }
        usedIds.add(candidate);
        return candidate;
    }

    // =====================================================
    // MISC UTILITIES
    // =====================================================

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

    // =====================================================
    // RECORDS
    // =====================================================

    private record GraphIndex(
            Map<String, List<String>> outgoing,
            Map<String, List<String>> incoming) {
    }

    private record NodeLayout(double x, double y, double width, double height) {

        double right() {
            return x + width;
        }

        double centerY() {
            return y + (height / 2);
        }

        NodeLayout withY(double newY) {
            return new NodeLayout(x, newY, width, height);
        }
    }
}