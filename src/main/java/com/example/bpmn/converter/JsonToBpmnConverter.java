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
import org.camunda.bpm.model.bpmn.AssociationDirection;
import org.camunda.bpm.model.bpmn.BpmnModelException;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.ComplexGateway;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.Association;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.TextAnnotation;
import org.camunda.bpm.model.bpmn.instance.Text;
import org.camunda.bpm.model.bpmn.instance.Collaboration;
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

    // =====================================================
    // LAYOUT CONSTANTS
    // =====================================================

    private static final double START_X                        = 160;
    private static final double START_Y                        = 220;
    private static final double HORIZONTAL_GAP                 = 180;
    private static final double MIN_VERTICAL_GAP               = 190;
    private static final double LANE_PADDING                   = 70;
    private static final double EVENT_SIZE                     = 36;
    private static final double GATEWAY_SIZE                   = 50;
    private static final double TASK_WIDTH                     = 120;
    private static final double TASK_HEIGHT                    = 80;
    private static final double DATA_OBJECT_WIDTH              = 36;
    private static final double DATA_OBJECT_HEIGHT             = 50;
    private static final double DATA_STORE_WIDTH               = 50;
    private static final double DATA_STORE_HEIGHT              = 50;
    private static final double SUBPROCESS_COLLAPSED_WIDTH     = 180;
    private static final double SUBPROCESS_COLLAPSED_HEIGHT    = 110;
    private static final double ADHOC_DEFAULT_WIDTH            = 500;
    private static final double ADHOC_DEFAULT_HEIGHT           = 320;
    private static final double ORTHOGONAL_EDGE_OFFSET         = 48;
    private static final double SUBPROCESS_TOP_PADDING         = 40;
    private static final double SUBPROCESS_BOTTOM_PADDING      = 40;
    private static final double SUBPROCESS_LEFT_PADDING        = 52;
    private static final double SUBPROCESS_RIGHT_PADDING       = 90;
    private static final double NESTED_SUBPROCESS_MIN_GAP      = 42;
    private static final double EDGE_VERTICAL_SPLIT_THRESHOLD  = 110;
    private static final double LOOP_EDGE_MIN_DETOUR_X         = 150;
    private static final double MESSAGE_FLOW_MIN_CLEARANCE     = 30;
    private static final double LOOP_EDGE_COMPACT_THRESHOLD    = 350;
    private static final double LOOP_EDGE_MAX_COMPACT_DEPTH    = 120;
    private static final double MESSAGE_FLOW_VERTICAL_ALIGN_X   = 100;
    private static final double SUBPROCESS_TITLE_CLEARANCE     = 30;
    private static final double CONTAINER_RIGHT_NEIGHBOR_GAP   = 140;
    private static final Pattern NON_ID_CHARACTER              = Pattern.compile("[^A-Za-z0-9_.-]");

    // =====================================================
    // FACTORIES
    // =====================================================

    private final TaskFactory            taskFactory            = new TaskFactory();
    private final GatewayFactory         gatewayFactory         = new GatewayFactory();
    private final EventFactory           eventFactory           = new EventFactory();
    private final FlowFactory            flowFactory            = new FlowFactory();
    private final SubProcessFactory      subProcessFactory      = new SubProcessFactory();
    private final CallActivityFactory    callActivityFactory    = new CallActivityFactory();
    private final EventSubProcessFactory eventSubProcessFactory = new EventSubProcessFactory();
    private final TransactionFactory     transactionFactory     = new TransactionFactory();
    private final AdHocSubProcessFactory adHocSubProcessFactory = new AdHocSubProcessFactory();

    // =====================================================
    // STATE (reset per convert() call)
    // =====================================================

    /** Stores pre-computed (PASS 0) sizes and later (PASS 1) full NodeLayouts for every node. */
    private final Map<String, NodeLayout> globalNodeLayouts = new HashMap<>();

    /** IDs of every adHocSubProcess at any nesting depth, collected before model construction. */
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

        List<ElementDTO> elements = getElements(request, processDTO);

        // Pre-collect ALL adHoc IDs (top-level + nested) before any node is created.
        adHocSubProcessIds.clear();
        collectAdHocIds(elements);

        Map<String, FlowNode> nodesById = new LinkedHashMap<>();
        Map<String, BaseElement> elementsById = new LinkedHashMap<>();
        for (ElementDTO element : elements) {
            String elementId = requireElementId(element);
            if (nodesById.containsKey(elementId)) {
                throw new IllegalArgumentException("Duplicate BPMN element id: " + elementId);
            }
            if ("textAnnotation".equals(element.getType())) {
                TextAnnotation annotation = modelInstance.newInstance(TextAnnotation.class);
                annotation.setId(elementId);
                Text text = modelInstance.newInstance(Text.class);
                text.setTextContent(element.getName() == null ? "" : element.getName());
                annotation.setText(text);
                process.addChildElement(annotation);
                elementsById.put(annotation.getId(), annotation);
                continue;
            }
            if ("dataObjectReference".equals(element.getType())) {
                DataObjectReference dataObjectReference = modelInstance.newInstance(DataObjectReference.class);
                dataObjectReference.setId(elementId);
                setName(dataObjectReference, element.getName());
                process.addChildElement(dataObjectReference);
                elementsById.put(dataObjectReference.getId(), dataObjectReference);
                continue;
            }
            if ("dataStoreReference".equals(element.getType())) {
                DataStoreReference dataStoreReference = modelInstance.newInstance(DataStoreReference.class);
                dataStoreReference.setId(elementId);
                setName(dataStoreReference, element.getName());
                process.addChildElement(dataStoreReference);
                elementsById.put(dataStoreReference.getId(), dataStoreReference);
                continue;
            }
            FlowNode node = createFlowNode(modelInstance, process, element);
            nodesById.put(node.getId(), node);
            elementsById.put(node.getId(), node);
        }

        List<SequenceFlow> sequenceFlows = new ArrayList<>();
        List<BaseElement> nonSequenceFlows = new ArrayList<>();
        for (FlowDTO flow : getFlows(request, processDTO)) {
            String flowType = flow.getType() == null ? "sequenceFlow" : flow.getType();
            switch (flowType) {
                case "sequenceFlow" -> sequenceFlows.add(flowFactory.createSequenceFlow(modelInstance, process, flow, nodesById));
                case "messageFlow" -> nonSequenceFlows.add(flowFactory.createMessageFlow(modelInstance, ensureCollaboration(modelInstance, definitions, process), flow, elementsById));
                case "association" -> nonSequenceFlows.add(flowFactory.createAssociation(modelInstance, process, flow, elementsById, AssociationDirection.None));
                case "associationOne" -> nonSequenceFlows.add(flowFactory.createAssociation(modelInstance, process, flow, elementsById, AssociationDirection.One));
                case "associationBoth" -> nonSequenceFlows.add(flowFactory.createAssociation(modelInstance, process, flow, elementsById, AssociationDirection.Both));
                
                default -> throw new IllegalArgumentException("Unsupported BPMN flow type: " + flowType);
            }
        }

        applyCamundaModelApiLayout(modelInstance, process, nodesById, elementsById, sequenceFlows, nonSequenceFlows);
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
     * at every nesting depth. Must be called once before model construction.
     */
    private void collectAdHocIds(List<ElementDTO> elements) {
        if (elements == null) return;
        for (ElementDTO element : elements) {
            if ("adHocSubProcess".equals(element.getType())) {
                adHocSubProcessIds.add(element.getId());
            }
            collectAdHocIds(element.getElements());
        }
    }

    // =====================================================
    // LAYOUT ENTRY — MULTI-PASS ORCHESTRATION
    // =====================================================

    /**
     * Four-pass layout engine:
     *
     * PASS 0 — Bottom-up size computation (deepest containers first, no positions assigned)
     * PASS 1 — Top-down position assignment using final sizes from PASS 0
     * PASS 2 — Neighbor displacement: push right-neighbors away from grown containers
     * PASS 3+4 — Orthogonal edge routing and BPMN DI generation
     */
    private void applyCamundaModelApiLayout(
            BpmnModelInstance modelInstance,
            Process process,
            Map<String, FlowNode> nodesById,
            Map<String, BaseElement> elementsById,
            List<SequenceFlow> sequenceFlows,
            List<BaseElement> extraEdges
    ) {
        try {
            Definitions definitions = modelInstance.getDefinitions();
            definitions.setTargetNamespace(BpmnModelConstants.BPMN20_NS);
            new ArrayList<>(definitions.getBpmDiagrams()).forEach(definitions::removeChildElement);

            Set<String> usedDiIds = new HashSet<>();

            // ── PASS 0: Bottom-up size computation ──────────────────────────
            globalNodeLayouts.clear();
            computeContainerSizesBottomUp(nodesById);

            // ── PASS 1: Top-down position assignment using final sizes ───────
            Map<String, NodeLayout> nodeLayouts = assignPositionsTopDown(nodesById, sequenceFlows);
            globalNodeLayouts.putAll(nodeLayouts);

            // ── PASS 2: Neighbor displacement ────────────────────────────────
            GraphIndex graph = buildGraphIndex(nodesById, sequenceFlows);
            Map<String, Integer> levels = calculateLevels(nodesById.keySet(), graph);
            Map<Integer, List<FlowNode>> nodesByLevel = groupNodesByLevel(nodesById.values(), levels);
            Map<String, Integer> levelByNodeId = new HashMap<>();
            nodesByLevel.forEach((lvl, nodes) -> nodes.forEach(n -> levelByNodeId.put(n.getId(), lvl)));

            resolveContainerOverlaps(nodeLayouts, nodesByLevel, levelByNodeId);
            recenterJoinNodes(nodeLayouts, graph, nodesByLevel, levelByNodeId);
            globalNodeLayouts.putAll(nodeLayouts);

            // ── PASS 3+4: BPMN DI generation with corrected edge routing ─────
            BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
            diagram.setId(stableDiId("BPMNDiagram", process.getId(), modelInstance, usedDiIds));
            BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
            plane.setId(stableDiId("BPMNPlane", process.getId(), modelInstance, usedDiIds));
            Collaboration collaboration = definitions.getChildElementsByType(Collaboration.class).stream().findFirst().orElse(null);
            boolean hasMessageFlow = extraEdges.stream().anyMatch(MessageFlow.class::isInstance);
            plane.setBpmnElement(hasMessageFlow && collaboration != null ? collaboration : process);
            diagram.setBpmnPlane(plane);
            definitions.addChildElement(diagram);

            Map<String, NodeLayout> allLayouts = new LinkedHashMap<>(nodeLayouts);
            allLayouts.putAll(computeArtifactLayouts(elementsById, extraEdges, nodeLayouts));

            for (FlowNode node : nodesById.values()) {
                createBpmnShape(modelInstance, plane, node, allLayouts.get(node.getId()), usedDiIds);
            }
            for (BaseElement element : elementsById.values()) {
                if (!(element instanceof FlowNode) && allLayouts.containsKey(element.getId())) {
                    createBpmnShape(modelInstance, plane, element, allLayouts.get(element.getId()), usedDiIds);
                }
            }
            for (SequenceFlow sequenceFlow : sequenceFlows) {
                createBpmnEdgeRouted(modelInstance, plane, sequenceFlow, allLayouts, usedDiIds);
            }
            for (BaseElement edge : extraEdges) {
                createGenericBpmnEdge(modelInstance, plane, edge, allLayouts, usedDiIds);
            }
            renderExpandedAdHocContents(modelInstance, plane, nodesById.values(), nodeLayouts, usedDiIds);

            createCollapsedSubProcessDiagrams(modelInstance, definitions, nodesById.values(),
                    usedDiIds, nodeLayouts);

        } catch (BpmnModelException ex) {
            throw new IllegalArgumentException("Unable to generate BPMN diagram layout", ex);
        }
    }

    // =====================================================
    // PASS 0 — BOTTOM-UP SIZE COMPUTATION
    // =====================================================

    /**
     * Recursively measures every container (SubProcess, Transaction, AdHoc) bottom-up.
     * Sizes are stored in globalNodeLayouts with placeholder position (0, 0).
     * No positions are assigned here — only width and height.
     */
    private void computeContainerSizesBottomUp(Map<String, FlowNode> nodesById) {
        for (FlowNode node : nodesById.values()) {
            if (!(node instanceof SubProcess subProcess)) continue;

            Map<String, FlowNode> children = collectChildNodes(subProcess);
            List<SequenceFlow> childFlows  = collectChildFlows(subProcess);

            if (children.isEmpty()) {
                // Empty or collapsed subprocess — use fixed collapsed dimensions
                globalNodeLayouts.put(subProcess.getId(),
                        new NodeLayout(0, 0, SUBPROCESS_COLLAPSED_WIDTH, SUBPROCESS_COLLAPSED_HEIGHT));
                continue;
            }

            // CRITICAL: recurse first so child containers are sized before the parent is measured
            computeContainerSizesBottomUp(children);

            if (node instanceof Transaction transaction) {

    globalNodeLayouts.put(
        transaction.getId(),
        measureTransactionContainer(children, childFlows)
    );

} else if (adHocSubProcessIds.contains(subProcess.getId())) {

    globalNodeLayouts.put(
        subProcess.getId(),
        measureAdHocContainer(children)
    );

} else {

    globalNodeLayouts.put(
        subProcess.getId(),
        measureExpandedSubProcessContainer(children, childFlows)
    );
}
        }
    }
private NodeLayout measureTransactionContainer(
        Map<String, FlowNode> children,
        List<SequenceFlow> childFlows) {

    Map<String, NodeLayout> localLayouts =
            calculateNodeLayouts(
                    children,
                    childFlows,
                    SUBPROCESS_LEFT_PADDING,
                    SUBPROCESS_TOP_PADDING
            );

    double maxRight = 0;
    double maxBottom = 0;

    for (NodeLayout l : localLayouts.values()) {
        maxRight = Math.max(maxRight, l.x() + l.width());
        maxBottom = Math.max(maxBottom, l.y() + l.height());
    }

    double width  = maxRight  + SUBPROCESS_RIGHT_PADDING;
    double height = maxBottom + SUBPROCESS_BOTTOM_PADDING;
    width  = Math.max(width,  320);
    height = Math.max(height, 180);

    return new NodeLayout(0, 0, width, height);
    
}
    /**
     * Measures an expanded subprocess by running a layout in local (left-padding, top-padding)
     * space, computing the bounding box of all children, then adding container padding.
     */
    private NodeLayout measureExpandedSubProcessContainer(
            Map<String, FlowNode> children,
            List<SequenceFlow> childFlows) {

        // Use top-left of content area as local origin so padding is included in the measurement
        double localStartX = SUBPROCESS_LEFT_PADDING;
        double localStartY = SUBPROCESS_TOP_PADDING + SUBPROCESS_TITLE_CLEARANCE;

        Map<String, NodeLayout> localLayouts = calculateNodeLayouts(
                children, childFlows, localStartX, localStartY);

        double maxRight  = 0;
        double maxBottom = 0;
        for (NodeLayout l : localLayouts.values()) {
            maxRight  = Math.max(maxRight,  l.x() + l.width());
            maxBottom = Math.max(maxBottom, l.y() + l.height());
        }

        double width  = maxRight  + SUBPROCESS_RIGHT_PADDING;
        double height = maxBottom + SUBPROCESS_BOTTOM_PADDING;

        width  = Math.max(width,  300);
        height = Math.max(height, 160);

        return new NodeLayout(0, 0, width, height); // x,y are placeholders; PASS 1 sets them
    }

    /**
     * Measures an AdHoc subprocess using a 2-column grid layout in local coordinate space.
     */
   private NodeLayout measureAdHocContainer(Map<String, FlowNode> children) {
    int count = children.size();

    int cols = Math.min(2, Math.max(1, count));
    int rows = (int) Math.ceil((double) count / cols);

    double taskGapX = 40;
    double taskGapY = 40;

    double innerWidth =
            (cols * TASK_WIDTH)
            + ((cols - 1) * taskGapX);

    double innerHeight =
            (rows * TASK_HEIGHT)
            + ((rows - 1) * taskGapY);

    double width =
            innerWidth
            + SUBPROCESS_LEFT_PADDING
            + SUBPROCESS_RIGHT_PADDING;

    double height =
            innerHeight
            + SUBPROCESS_TOP_PADDING
            + SUBPROCESS_BOTTOM_PADDING
            + SUBPROCESS_TITLE_CLEARANCE;

    // tailles minimales raisonnables
    width = Math.max(width, 320);
    height = Math.max(height, 180);

    return new NodeLayout(0, 0, width, height);
}

    // =====================================================
    // PASS 1 — TOP-DOWN POSITION ASSIGNMENT
    // =====================================================

    /**
     * Runs the standard BFS/topological layout using final container sizes from PASS 0.
     * After layout, merges PASS-0 sizes with PASS-1 positions so every NodeLayout in the
     * returned map has correct (x, y, width, height).
     */
    private Map<String, NodeLayout> assignPositionsTopDown(
            Map<String, FlowNode> nodesById,
            List<SequenceFlow> sequenceFlows) {

        // calculateNodeLayouts calls getNodeWidth/getNodeHeight, which now read from
        // globalNodeLayouts (populated in PASS 0) for containers — so sizes are final.
        Map<String, NodeLayout> positioned = calculateNodeLayouts(
                nodesById, sequenceFlows, START_X, START_Y);

        // Merge: take x,y from PASS 1 and width,height from PASS 0 for containers
        Map<String, NodeLayout> merged = new HashMap<>();
        for (Map.Entry<String, NodeLayout> entry : positioned.entrySet()) {
            String     id          = entry.getKey();
            NodeLayout posLayout   = entry.getValue();
            NodeLayout sizeLayout  = globalNodeLayouts.get(id);

            if (sizeLayout != null) {
                // Container: real position + pre-computed size
                merged.put(id, new NodeLayout(
                        posLayout.x(), posLayout.y(),
                        sizeLayout.width(), sizeLayout.height()));
            } else {
                merged.put(id, posLayout);
            }
        }
        return merged;
    }

    // =====================================================
    // PASS 2 — NEIGHBOR DISPLACEMENT (COLLISION RESOLUTION)
    // =====================================================

    /**
     * After containers are positioned with their final sizes, any right-neighbor that
     * now overlaps a container is shifted right (and all nodes at its level and beyond).
     */
    private void resolveContainerOverlaps(
            Map<String, NodeLayout> nodeLayouts,
            Map<Integer, List<FlowNode>> nodesByLevel,
            Map<String, Integer> levelByNodeId) {

        List<Integer> sortedLevels = nodesByLevel.keySet().stream().sorted().toList();

        for (int levelIdx = 0; levelIdx < sortedLevels.size(); levelIdx++) {
            int level = sortedLevels.get(levelIdx);

            for (FlowNode node : nodesByLevel.get(level)) {
                if (!(node instanceof SubProcess) && !(node instanceof Transaction)) continue;

                NodeLayout containerLayout = nodeLayouts.get(node.getId());
                if (containerLayout == null) continue;

                double requiredClearance = containerLayout.right() + CONTAINER_RIGHT_NEIGHBOR_GAP;

                // Safety net only: dynamic level placement should already reserve
                // container width. If a later balancing step moved a level too far
                // left, shift the entire level and every subsequent level together
                // to preserve column spacing.
                for (int nextIdx = levelIdx + 1; nextIdx < sortedLevels.size(); nextIdx++) {
                    int nextLevel = sortedLevels.get(nextIdx);
                    
                    double levelLeft = minLevelX(nodeLayouts, nodesByLevel.get(nextLevel));
                    if (levelLeft < requiredClearance) {
                        shiftLevelsRight(nodeLayouts, nodesByLevel, sortedLevels,
                                nextIdx, requiredClearance - levelLeft);
                    }
                }
            }
        }
 
        // Final safety pass: every level must begin after the real max-right of
        // the previous level. This catches any non-container overlap without
        // changing routing or BPMN DI generation.
        for (int levelIdx = 0; levelIdx < sortedLevels.size() - 1; levelIdx++) {
            int currentLevel = sortedLevels.get(levelIdx);
            int nextLevel = sortedLevels.get(levelIdx + 1);

 
            double maxRight = maxLevelRight(nodeLayouts, nodesByLevel.get(currentLevel));
            double levelLeft = minLevelX(nodeLayouts, nodesByLevel.get(nextLevel));
            double requiredX = maxRight + HORIZONTAL_GAP;

            if (levelLeft < requiredX) {
                shiftLevelsRight(nodeLayouts, nodesByLevel, sortedLevels,
                        levelIdx + 1, requiredX - levelLeft);
            }
        }
    }

    private double minLevelX(
            Map<String, NodeLayout> nodeLayouts,
            List<FlowNode> levelNodes) {


        return levelNodes.stream()
                .map(node -> nodeLayouts.get(node.getId()))
                .filter(java.util.Objects::nonNull)
                .mapToDouble(NodeLayout::x)
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private double maxLevelRight(
            Map<String, NodeLayout> nodeLayouts,
            List<FlowNode> levelNodes) {

        return levelNodes.stream()
                .map(node -> nodeLayouts.get(node.getId()))
                .filter(java.util.Objects::nonNull)
                .mapToDouble(NodeLayout::right)
                .max()
                .orElse(START_X);
    }

    private void shiftLevelsRight(
            Map<String, NodeLayout> nodeLayouts,
            Map<Integer, List<FlowNode>> nodesByLevel,
            List<Integer> sortedLevels,
            int firstLevelIndex,
            double shift) {

           
        if (shift <= 0) return;
        for (int idx = firstLevelIndex; idx < sortedLevels.size(); idx++) {
            int level = sortedLevels.get(idx);
            for (FlowNode node : nodesByLevel.get(level)) {
                NodeLayout layout = nodeLayouts.get(node.getId());
                if (layout == null) continue;
                nodeLayouts.put(node.getId(), new NodeLayout(
                        layout.x() + shift,
                        layout.y(),
                        layout.width(),
                        layout.height()));
            }
        }
    }


   private void recenterJoinNodes(
        Map<String, NodeLayout> nodeLayouts,
        GraphIndex graph,
        Map<Integer, List<FlowNode>> nodesByLevel,
        Map<String, Integer> levelByNodeId) {

    for (Map.Entry<Integer, List<FlowNode>> levelEntry : nodesByLevel.entrySet()) {
        int level = levelEntry.getKey();
        List<FlowNode> levelNodes = levelEntry.getValue();

        for (FlowNode node : levelNodes) {
            List<String> incomingIds = graph.incoming().getOrDefault(node.getId(), List.of());
            if (incomingIds.size() < 2) continue;

            List<NodeLayout> predecessorLayouts = incomingIds.stream()
                    .map(nodeLayouts::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (predecessorLayouts.size() < 2) continue;

            double avgCenterY = predecessorLayouts.stream()
                    .mapToDouble(NodeLayout::centerY)
                    .average()
                    .orElse(0);

            NodeLayout cur = nodeLayouts.get(node.getId());
            if (cur == null) continue;

            double newY = Math.max(START_Y, avgCenterY - cur.height() / 2);
            NodeLayout candidate = cur.withY(newY);

            // FIX: never place a recentered node where it overlaps a sibling
            // at the same level (e.g. an expanded subprocess). If it does,
            // push the candidate below that sibling instead.
            boolean collides = true;
            int guard = 0;
            while (collides && guard < 20) {
                collides = false;
                for (FlowNode sibling : levelNodes) {
                    if (sibling.getId().equals(node.getId())) continue;
                    NodeLayout siblingLayout = nodeLayouts.get(sibling.getId());
                    if (siblingLayout == null) continue;

                    boolean overlapsX = candidate.x() < siblingLayout.right()
                            && candidate.right() > siblingLayout.x();
                    boolean overlapsY = candidate.y() < siblingLayout.y() + siblingLayout.height()
                            && candidate.y() + candidate.height() > siblingLayout.y();

                    if (overlapsX && overlapsY) {
                        double pushedY = siblingLayout.y() + siblingLayout.height() + 40;
                        candidate = candidate.withY(pushedY);
                        collides = true;
                    }
                }
                guard++;
            }

            nodeLayouts.put(node.getId(), candidate);
        }
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
            Map<String, NodeLayout> parentLayouts) {

        for (FlowNode node : nodes) {
            if (node instanceof SubProcess subProcess) {
                if (adHocSubProcessIds.contains(subProcess.getId())) {
                    continue;
                }
                createSubProcessDiagram(modelInstance, definitions, subProcess, usedDiIds, parentLayouts);
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
            Map<String, NodeLayout> parentLayouts) {

        Map<String, FlowNode> subNodes = new LinkedHashMap<>();
        List<SequenceFlow> subFlows    = new ArrayList<>();

        for (FlowElement element : subProcess.getFlowElements()) {
            if (element instanceof FlowNode node)   subNodes.put(node.getId(), node);
            if (element instanceof SequenceFlow flow) subFlows.add(flow);
        }
        if (subNodes.isEmpty()) return;

        BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
        diagram.setId(stableDiId("BPMNDiagram", subProcess.getId(), modelInstance, usedDiIds));
        BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
        plane.setId(stableDiId("BPMNPlane", subProcess.getId(), modelInstance, usedDiIds));
        plane.setBpmnElement(subProcess);
        diagram.setBpmnPlane(plane);
        definitions.addChildElement(diagram);

        NodeLayout parentLayout = parentLayouts.get(subProcess.getId());
        Map<String, NodeLayout> subLayouts;

        if (adHocSubProcessIds.contains(subProcess.getId())) {
            // parentLayout carries the final (x,y,w,h) from PASS 1+2
            subLayouts = calculateAdHocLayouts(subNodes, subProcess, parentLayout);
        } else {
            subLayouts = calculateSubProcessLayouts(subNodes, subFlows, parentLayout);
        }

        for (FlowNode node : subNodes.values()) {
            createBpmnShape(modelInstance, plane, node, subLayouts.get(node.getId()), usedDiIds);
        }
        for (SequenceFlow flow : subFlows) {
            createBpmnEdgeRouted(modelInstance, plane, flow, subLayouts, usedDiIds);
        }

        // Recurse for nested SubProcess / Transaction / AdHoc at any depth
        createCollapsedSubProcessDiagrams(modelInstance, definitions, subNodes.values(),
                usedDiIds, subLayouts);
    }

    private void createTransactionDiagram(
            BpmnModelInstance modelInstance,
            Definitions definitions,
            Transaction transaction,
            Set<String> usedDiIds) {

        Map<String, FlowNode> subNodes = new LinkedHashMap<>();
        List<SequenceFlow> subFlows    = new ArrayList<>();

        for (FlowElement element : transaction.getFlowElements()) {
            if (element instanceof FlowNode node)    subNodes.put(node.getId(), node);
            if (element instanceof SequenceFlow flow) subFlows.add(flow);
        }
        if (subNodes.isEmpty()) return;

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
            createBpmnEdgeRouted(modelInstance, plane, flow, subLayouts, usedDiIds);
        }

        // Recurse for any nested SubProcess or Transaction inside this Transaction
        createCollapsedSubProcessDiagrams(modelInstance, definitions, subNodes.values(),
                usedDiIds, subLayouts);
    }

    // =====================================================
    // LAYOUT CALCULATION — CORE (topological BFS)
    // =====================================================

    private Map<String, NodeLayout> calculateNodeLayouts(
            Map<String, FlowNode> nodesById,
            List<SequenceFlow> sequenceFlows,
            double startX,
            double centerY) {

        GraphIndex graph = buildGraphIndex(nodesById, sequenceFlows);
        Map<String, Integer> levels = calculateLevels(nodesById.keySet(), graph);
        Map<Integer, List<FlowNode>> nodesByLevel = groupNodesByLevel(nodesById.values(), levels);
        orderLevelsForReadableEdges(nodesByLevel, graph);

        Map<String, NodeLayout> nodeLayouts = new HashMap<>();
        Map<Integer, Double> levelXPositions = calculateLevelXPositions(
                nodesByLevel, graph, startX);

        for (Map.Entry<Integer, List<FlowNode>> entry : nodesByLevel.entrySet()) {
            int level            = entry.getKey();
            List<FlowNode> levelNodes = entry.getValue();
            double verticalGap   = verticalGapFor(levelNodes);
            double totalHeight   = totalLevelHeight(levelNodes, verticalGap);
            double y             = Math.max(START_Y, centerY - (totalHeight / 2));
            double levelX        = levelXPositions.getOrDefault(level, startX);

            for (FlowNode node : levelNodes) {
                double width  = getNodeWidth(node);
                double height = getNodeHeight(node);
                nodeLayouts.put(node.getId(),
                        new NodeLayout(levelX, y, width, height));
                y += height + verticalGap;
            }
        }
        return balanceGatewayBranches(nodeLayouts, nodesByLevel, graph);
    }

    /**
     * Computes dynamic x coordinates for each topological level before any node is
     * placed. The next level starts after the real right boundary of the previous
     * level, so expanded subprocesses/transactions/ad-hoc subprocesses reserve
     * their final measured width up front instead of being repaired afterward.
     */
    private Map<Integer, Double> calculateLevelXPositions(
            Map<Integer, List<FlowNode>> nodesByLevel,
            GraphIndex graph,
            double startX) {

        Map<Integer, Double> levelXPositions = new HashMap<>();
        List<Integer> sortedLevels = nodesByLevel.keySet().stream().sorted().toList();
        double nextX = startX;

        for (int i = 0; i < sortedLevels.size(); i++) {
            int level = sortedLevels.get(i);
            List<FlowNode> levelNodes = nodesByLevel.get(level);

            if (i > 0) {
                nextX += horizontalReservationBeforeLevel(levelNodes, graph);
            }

            levelXPositions.put(level, nextX);

            double maxRight = nextX;
            for (FlowNode node : levelNodes) {
                maxRight = Math.max(maxRight, nextX + getNodeWidth(node));
            }
            nextX = maxRight + HORIZONTAL_GAP;
        }

        return levelXPositions;
    }

    private double horizontalReservationBeforeLevel(
            List<FlowNode> levelNodes,
            GraphIndex graph) {

        boolean hasEndEvent = levelNodes.stream().anyMatch(EndEvent.class::isInstance);
        int maxIncoming = levelNodes.stream()
                .mapToInt(node -> graph.incoming().getOrDefault(node.getId(), List.of()).size())
                .max()
                .orElse(0);

        double reservation = 0;
        if (hasEndEvent) {
            reservation += 90;
        }
        if (maxIncoming > 1) {
            reservation += Math.min(400, 120.0 * maxIncoming);
        }
        return reservation;
    }


    // =====================================================
    // SUBPROCESS INTERNAL LAYOUT
    // =====================================================

    /**
     * Positions children of an expanded subprocess inside the parent container.
     *
     * FIX (was): used parentLayout.height()/2 as centerY, which pushed children
     *            above the title bar for tall containers.
     * FIX (now): uses the true center of the content area (between title bar and bottom padding).
     */
    private Map<String, NodeLayout> calculateSubProcessLayouts(
            Map<String, FlowNode> subNodes,
            List<SequenceFlow> subFlows,
            NodeLayout parentLayout) {

        double contentTop = parentLayout.y()
                + SUBPROCESS_TOP_PADDING
                + SUBPROCESS_TITLE_CLEARANCE;

        double contentBottom = parentLayout.y() + parentLayout.height() - SUBPROCESS_BOTTOM_PADDING;
        double contentCenterY = (contentTop + contentBottom) / 2.0;
        double contentLeft    = parentLayout.x() + SUBPROCESS_LEFT_PADDING;

        Map<String, NodeLayout> rawLayouts = calculateNodeLayouts(
                subNodes, subFlows, contentLeft, contentCenterY);

        // Clamp: no child may be positioned above the title-safe line
        Map<String, NodeLayout> adjusted = new HashMap<>();
        for (Map.Entry<String, NodeLayout> entry : rawLayouts.entrySet()) {
            NodeLayout l = entry.getValue();
            double safeY = Math.max(l.y(), contentTop);
            adjusted.put(entry.getKey(), l.withY(safeY));
        }

        return enforceMinimumVerticalSpacing(adjusted);
    }

    /**
     * Positions children of an AdHocSubProcess in a 2-column grid relative to
     * the parent container's final (post-PASS-2) position.
     *
     * FIX (was): computed positions relative to placeholder (0,0) coordinates.
     * FIX (now): uses the real parentLayout.x() and parentLayout.y() from globalNodeLayouts.
     */
    private Map<String, NodeLayout> calculateAdHocLayouts(
            Map<String, FlowNode> nodesById,
            SubProcess subProcess,
            NodeLayout parentLayout) {

        if (parentLayout == null) {
            throw new IllegalArgumentException(
                    "Missing layout for adhoc subprocess: " + subProcess.getId());
        }

        double contentLeft = parentLayout.x() + SUBPROCESS_LEFT_PADDING;
        double contentTop  = parentLayout.y() + SUBPROCESS_TOP_PADDING + SUBPROCESS_TITLE_CLEARANCE;

        double colSpacing  = HORIZONTAL_GAP;
        double rowSpacing  = Math.max(MIN_VERTICAL_GAP, TASK_HEIGHT + 90);

        Map<String, NodeLayout> layouts = new HashMap<>();
        int index = 0;
        for (FlowNode node : nodesById.values()) {
            int col  = index % 2;
            int row  = index / 2;
            double x = contentLeft + (col * colSpacing);
            double y = contentTop  + (row * rowSpacing);
            layouts.put(node.getId(),
                    new NodeLayout(x, y, getNodeWidth(node), getNodeHeight(node)));
            index++;
        }
        return layouts;
    }

    // =====================================================
    // GRAPH BUILDING
    // =====================================================

    private GraphIndex buildGraphIndex(
            Map<String, FlowNode> nodesById,
            List<SequenceFlow> sequenceFlows) {

        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        nodesById.keySet().forEach(id -> {
            outgoing.put(id, new ArrayList<>());
            incoming.put(id, new ArrayList<>());
        });

        for (SequenceFlow sf : sequenceFlows) {
            String srcId = sf.getSource().getId();
            String tgtId = sf.getTarget().getId();
            if (nodesById.containsKey(srcId) && nodesById.containsKey(tgtId)) {
                outgoing.get(srcId).add(tgtId);
                incoming.get(tgtId).add(srcId);
            }
        }
        return new GraphIndex(outgoing, incoming);
    }

    private Map<String, Integer> calculateLevels(Collection<String> nodeIds, GraphIndex graph) {
        Map<String, Integer> levels   = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Queue<String> ready           = new ArrayDeque<>();
        Set<String> visited           = new HashSet<>();
        Set<String> loopEdges         = new HashSet<>();



        for (String nodeId : nodeIds) {
            levels.put(nodeId, 0);
            int inCount = graph.incoming().getOrDefault(nodeId, List.of()).size();
            indegree.put(nodeId, inCount);
            if (inCount == 0) ready.add(nodeId);
        }

         while (visited.size() < nodeIds.size()) {
            if (ready.isEmpty()) {
                nodeIds.stream().filter(id -> !visited.contains(id)).findFirst().ifPresent(ready::add);
            }
            String srcId = ready.remove();
            if (!visited.add(srcId)) {
                continue;
            }
        int sourceLevel = levels.getOrDefault(srcId, 0);
            for (String tgtId : graph.outgoing().getOrDefault(srcId, List.of())) {
                int targetLevel = levels.getOrDefault(tgtId, 0);

                // Backward/loop edges must not influence layering.
                if (visited.contains(tgtId) && targetLevel <= sourceLevel) {
                    loopEdges.add(srcId + "->" + tgtId);
                    continue;
                }

                int candidate = sourceLevel + 1;
                if (candidate > targetLevel) {
                    levels.put(tgtId, candidate);
                }

                int newIndegree = indegree.merge(tgtId, -1, Integer::sum);
                if (newIndegree <= 0) {
                    ready.add(tgtId);
                }
            }
        }
        return levels;
    }

    private Map<Integer, List<FlowNode>> groupNodesByLevel(
            Collection<FlowNode> nodes,
            Map<String, Integer> levels) {

        Map<Integer, List<FlowNode>> nodesByLevel = new LinkedHashMap<>();
        nodes.stream()
                .sorted(Comparator.comparingInt(n -> levels.getOrDefault(n.getId(), 0)))
                .forEach(n -> nodesByLevel
                        .computeIfAbsent(levels.getOrDefault(n.getId(), 0), ignored -> new ArrayList<>())
                        .add(n));
        return nodesByLevel;
    }

    private void orderLevelsForReadableEdges(
            Map<Integer, List<FlowNode>> nodesByLevel,
            GraphIndex graph) {

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
        OptionalDouble avg = relatedNodeIds.stream()
                .filter(order::containsKey)
                .mapToInt(order::get)
                .average();
        return avg.orElse(0);
    }

    private Map<String, NodeLayout> balanceGatewayBranches(
            Map<String, NodeLayout> nodeLayouts,
            Map<Integer, List<FlowNode>> nodesByLevel,
            GraphIndex graph) {

        Map<String, NodeLayout> balanced = new HashMap<>(nodeLayouts);
        Map<String, Integer> levelByNodeId = new HashMap<>();
        nodesByLevel.forEach((level, nodes) -> nodes.forEach(n -> levelByNodeId.put(n.getId(), level)));

        for (Map.Entry<Integer, List<FlowNode>> entry : nodesByLevel.entrySet()) {
            for (FlowNode node : entry.getValue()) {
                List<String> outgoing = graph.outgoing().getOrDefault(node.getId(), List.of());
                if (!isGateway(node) || outgoing.size() < 2) continue;

                List<String> nextLevelTargets = outgoing.stream()
                        .filter(tId -> levelByNodeId.getOrDefault(tId, -1) == entry.getKey() + 1)
                        .filter(tId -> graph.incoming().getOrDefault(tId, List.of()).size() == 1)
                        .toList();
                if (nextLevelTargets.size() < 2) continue;

                double gatewayCenterY = balanced.get(node.getId()).centerY();
                double branchGap =
                Math.max(MIN_VERTICAL_GAP + 80, verticalGapForLayouts(nextLevelTargets, balanced));
                double firstCenterY   = gatewayCenterY - ((nextLevelTargets.size() - 1) * branchGap / 2);

                for (int i = 0; i < nextLevelTargets.size(); i++) {
                    String tId    = nextLevelTargets.get(i);
                    NodeLayout cur = balanced.get(tId);
                    double newY   = firstCenterY + (i * branchGap) - (cur.height() / 2);
                    balanced.put(tId, cur.withY(Math.max(START_Y, newY)));
                }
            }
        }
        normalizeLevelSpacing(balanced, nodesByLevel);
        return balanced;
    }

    private void normalizeLevelSpacing(
            Map<String, NodeLayout> nodeLayouts,
            Map<Integer, List<FlowNode>> nodesByLevel) {

        for (List<FlowNode> levelNodes : nodesByLevel.values()) {
            levelNodes.sort(Comparator.comparingDouble(n -> nodeLayouts.get(n.getId()).centerY()));
            double nextY = START_Y;
            for (FlowNode node : levelNodes) {
                NodeLayout cur = nodeLayouts.get(node.getId());
                if (cur.y() < nextY) {
                    cur = cur.withY(nextY);
                    nodeLayouts.put(node.getId(), cur);
                }
                nextY = cur.y() + cur.height() + verticalGapFor(levelNodes);
            }
        }
    }

    private double verticalGapFor(List<FlowNode> nodes) {
        boolean hasGateway = nodes.stream().anyMatch(this::isGateway);
        return Math.max(MIN_VERTICAL_GAP + 20, (hasGateway ? 190 : 160) + (nodes.size() * 8));
    }

    private double verticalGapForLayouts(
            List<String> nodeIds,
            Map<String, NodeLayout> nodeLayouts) {

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
    // VERTICAL SPACING ENFORCEMENT
    // =====================================================

    private Map<String, NodeLayout> enforceMinimumVerticalSpacing(Map<String, NodeLayout> layouts) {
        List<Map.Entry<String, NodeLayout>> ordered = new ArrayList<>(layouts.entrySet());
        ordered.sort(Comparator.comparingDouble(e -> e.getValue().y()));

        Map<String, NodeLayout> adjusted = new HashMap<>(layouts);
        double nextAllowedY = -Double.MAX_VALUE;

        for (Map.Entry<String, NodeLayout> entry : ordered) {
            NodeLayout cur = adjusted.get(entry.getKey());
            if (cur.y() < nextAllowedY) {
                cur = cur.withY(nextAllowedY);
                adjusted.put(entry.getKey(), cur);
            }
            nextAllowedY = cur.y() + cur.height() + NESTED_SUBPROCESS_MIN_GAP;
        }
        return adjusted;
    }

    // =====================================================
    // SHAPE CREATION
    // =====================================================

    private void createBpmnShape(
            BpmnModelInstance modelInstance,
            BpmnPlane plane,
            FlowNode node,
            NodeLayout layout,
            Set<String> usedDiIds) {

        if (layout == null) {
            throw new IllegalArgumentException("No diagram layout found for node: " + node.getId());
        }

        BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
        shape.setId(stableDiId("BPMNShape", node.getId(), modelInstance, usedDiIds));
        shape.setBpmnElement(node);

        if (node instanceof SubProcess) {
            shape.setExpanded(false);
            shape.setHorizontal(true);
        }
        if (node instanceof Transaction) {
            shape.setExpanded(adHocSubProcessIds.contains(node.getId()));
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
     private void createBpmnShape(
            BpmnModelInstance modelInstance,
            BpmnPlane plane,
            BaseElement element,
            NodeLayout layout,
            Set<String> usedDiIds) {

        if (layout == null) return;
        BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
        shape.setId(stableDiId("BPMNShape", element.getId(), modelInstance, usedDiIds));
        shape.setBpmnElement(element);
        Bounds bounds = modelInstance.newInstance(Bounds.class);
        bounds.setX(layout.x());
        bounds.setY(layout.y());
        bounds.setWidth(layout.width());
        bounds.setHeight(layout.height());
        shape.setBounds(bounds);
        plane.addChildElement(shape);
    }

    private Map<String, NodeLayout> computeArtifactLayouts(Map<String, BaseElement> elementsById, List<BaseElement> extraEdges, Map<String, NodeLayout> nodeLayouts) {
        Map<String, NodeLayout> artifactLayouts = new LinkedHashMap<>();
        for (BaseElement edge : extraEdges) {
            if (!(edge instanceof Association association)) continue;
            BaseElement source = association.getSource();
            BaseElement target = association.getTarget();
            if (source == null || target == null) continue;

            if (source instanceof FlowNode && !(target instanceof FlowNode) && elementsById.containsKey(target.getId())) {
                NodeLayout src = nodeLayouts.get(source.getId());
                if (src != null && !artifactLayouts.containsKey(target.getId())) {
                    artifactLayouts.put(target.getId(), placeArtifactNearNode(target, src, nodeLayouts, artifactLayouts));
                }
            }
            if (target instanceof FlowNode && !(source instanceof FlowNode) && elementsById.containsKey(source.getId())) {
                NodeLayout tgt = nodeLayouts.get(target.getId());
                if (tgt != null && !artifactLayouts.containsKey(source.getId())) {
                    artifactLayouts.put(source.getId(), new NodeLayout(tgt.x() - 200, tgt.y() - 20, 140, 50));
                }
            }
        }
        return artifactLayouts;
    }
    private NodeLayout placeArtifactNearNode(BaseElement artifact, NodeLayout anchor, Map<String, NodeLayout> nodeLayouts, Map<String, NodeLayout> existingArtifactLayouts) {
        double width = getArtifactWidth(artifact);
        double height = getArtifactHeight(artifact);

        NodeLayout candidate = new NodeLayout(anchor.right() + 60, anchor.y() - 20, width, height);
        while (overlapsAny(candidate, nodeLayouts.values()) || overlapsAny(candidate, existingArtifactLayouts.values())) {
            candidate = candidate.withY(candidate.y() + height + 16);
        }
        return candidate;
    }

    private double getArtifactWidth(BaseElement artifact) {
        if (artifact instanceof DataObjectReference) return DATA_OBJECT_WIDTH;
        if (artifact instanceof DataStoreReference) return DATA_STORE_WIDTH;
        if (artifact instanceof TextAnnotation) return 140;
        return 100;
    }

    private double getArtifactHeight(BaseElement artifact) {
        if (artifact instanceof DataObjectReference) return DATA_OBJECT_HEIGHT;
        if (artifact instanceof DataStoreReference) return DATA_STORE_HEIGHT;
        if (artifact instanceof TextAnnotation) return 50;
        return 50;
    }

    private boolean overlapsAny(NodeLayout candidate, Collection<NodeLayout> layouts) {
        for (NodeLayout other : layouts) {
            boolean overlapX = candidate.x() < other.right() && candidate.right() > other.x();
            boolean overlapY = candidate.y() < other.y() + other.height() && candidate.y() + candidate.height() > other.y();
            if (overlapX && overlapY) {
                return true;
            }
        }
        return false;
    }
    private void renderExpandedAdHocContents(
            BpmnModelInstance modelInstance,
            BpmnPlane plane,
            Collection<FlowNode> nodes,
            Map<String, NodeLayout> parentLayouts,
            Set<String> usedDiIds) {

        for (FlowNode node : nodes) {
            if (!(node instanceof SubProcess subProcess)) {
                continue;
            }
            if (!adHocSubProcessIds.contains(subProcess.getId())) {
                continue;
            }

            Map<String, FlowNode> childNodes = collectChildNodes(subProcess);
            if (childNodes.isEmpty()) {
                continue;
            }
            List<SequenceFlow> childFlows = collectChildFlows(subProcess);

            NodeLayout parentLayout = parentLayouts.get(subProcess.getId());
            Map<String, NodeLayout> adHocLayouts = calculateAdHocLayouts(childNodes, subProcess, parentLayout);

            for (FlowNode childNode : childNodes.values()) {
                createBpmnShape(modelInstance, plane, childNode, adHocLayouts.get(childNode.getId()), usedDiIds);
            }
            for (SequenceFlow childFlow : childFlows) {
                createBpmnEdgeRouted(modelInstance, plane, childFlow, adHocLayouts, usedDiIds);
            }

            // recurse to support nested adhoc subprocesses
            renderExpandedAdHocContents(modelInstance, plane, childNodes.values(), adHocLayouts, usedDiIds);
        }
    }
    // =====================================================
    // PASS 3 — OBSTACLE-AWARE ORTHOGONAL EDGE ROUTING
    // =====================================================

    /**
     * Creates a BPMN edge with obstacle-aware orthogonal routing.
     * Replaces the old createBpmnEdge() which used stale coordinates
     * and had no awareness of container boundaries.
     */
    private void createBpmnEdgeRouted(
            BpmnModelInstance modelInstance,
            BpmnPlane plane,
            SequenceFlow sequenceFlow,
            Map<String, NodeLayout> nodeLayouts,
            Set<String> usedDiIds) {

        NodeLayout sourceLayout = nodeLayouts.get(sequenceFlow.getSource().getId());
        NodeLayout targetLayout = nodeLayouts.get(sequenceFlow.getTarget().getId());

        if (sourceLayout == null || targetLayout == null) {
            throw new IllegalArgumentException(
                    "No diagram layout found for sequence flow: " + sequenceFlow.getId());
        }

        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setId(stableDiId("BPMNEdge", sequenceFlow.getId(), modelInstance, usedDiIds));
        edge.setBpmnElement(sequenceFlow);

        boolean isForward = targetLayout.x() >= sourceLayout.right();
        boolean isLoop    = targetLayout.right() <= sourceLayout.x() + 10;

        if (isLoop) {
            routeLoopEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
        } else if (isForward) {
            routeForwardEdge(modelInstance, edge, sequenceFlow, sourceLayout, targetLayout, nodeLayouts);
        } else {
            // Same-level or crossing edge — route below both nodes
            routeAroundEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
        }

        plane.addChildElement(edge);
    }

    /**
     * Routes a forward (left-to-right) edge using an orthogonal elbow.
     * Detects containers whose interior intersects the naive mid-point and
     * reroutes to avoid passing through them.
     */
    private void routeForwardEdge(
        BpmnModelInstance modelInstance,
        BpmnEdge edge,
        SequenceFlow sequenceFlow,
        NodeLayout src,
        NodeLayout tgt,
        Map<String, NodeLayout> allLayouts) {

    double sx = src.right();
    double sy = src.centerY();
    double tx = tgt.x();
    double ty = gatewayIncomingTargetY(sequenceFlow, tgt, allLayouts);

    double horizontalDist = tx - sx;
    double verticalDist   = Math.abs(ty - sy);

    double midX = findClearMidpointX(sx, tx, sy, ty, allLayouts);
    Double mergeCorridorX = mergeIncomingCorridorX(sequenceFlow, sx, tx, allLayouts);

    addWaypoint(modelInstance, edge, sx, sy);
    if (mergeCorridorX != null) {
        double safeTy = findClearHorizontalY(mergeCorridorX, tx, ty, allLayouts);

        addWaypoint(modelInstance, edge, mergeCorridorX, sy);
        addWaypoint(modelInstance, edge, mergeCorridorX, safeTy);
        addWaypoint(modelInstance, edge, tx, safeTy);

        if (safeTy != ty) {
            addWaypoint(modelInstance, edge, tx, ty);
        }
        return;
    }

    if (verticalDist <= EDGE_VERTICAL_SPLIT_THRESHOLD) {
        addWaypoint(modelInstance, edge, midX, sy);
        addWaypoint(modelInstance, edge, midX, ty);
        addWaypoint(modelInstance, edge, tx, ty);
        return;
    }

    double splitX = sx + Math.max(40, horizontalDist * 0.33);
    double safeX  = Math.max(splitX, midX);

    // FIX: the LAST horizontal run (y=ty, from safeX to tx) was never checked.
    // That is the segment that actually cuts through tall containers like
    // an expanded subprocess sitting between safeX and tx.
    double safeTy = findClearHorizontalY(safeX, tx, ty, allLayouts);

    addWaypoint(modelInstance, edge, safeX, sy);
    addWaypoint(modelInstance, edge, safeX, safeTy);
    addWaypoint(modelInstance, edge, tx, safeTy);

    if (safeTy != ty) {
        // Detour was needed: come back down/up to the real target height
        // with one extra elbow right before reaching tx.
        addWaypoint(modelInstance, edge, tx, ty);
    }
}
 /**
     * Gives each incoming sequence flow to a merge target its own vertical
     * corridor. This keeps orthogonal routing intact while preventing multiple
     * incoming branches from sharing the same vertical segment immediately before
     * the merge gateway/node.
     */
    private Double mergeIncomingCorridorX(
            SequenceFlow sequenceFlow,
            double sourceRight,
            double targetLeft,
            Map<String, NodeLayout> allLayouts) {

        List<SequenceFlow> incoming = sortedIncomingSequenceFlows(sequenceFlow, allLayouts);
        if (incoming.size() <= 1) {
            return null;
        }

        int incomingIndex = incomingFlowIndex(sequenceFlow, incoming);
        if (incomingIndex < 0) {
            return null;
        }

        double corridorSpacing = 50;
        double targetPadding = 20;
        double corridorX = targetLeft
                - targetPadding
                - ((incoming.size() - 1 - incomingIndex) * corridorSpacing);

        if (corridorX <= sourceRight + targetPadding) {
            corridorX = sourceRight + targetPadding + (incomingIndex * corridorSpacing);
        }
        return corridorX;
    }

    /**
     * Visually separates incoming sequence flows on merge gateways by assigning
     * each flow a distinct target Y around the gateway center. Non-gateway
     * targets and gateways with a single incoming sequence flow keep centerY.
     */
    private double gatewayIncomingTargetY(
            SequenceFlow sequenceFlow,
            NodeLayout targetLayout,
            Map<String, NodeLayout> allLayouts) {

        if (sequenceFlow == null
                || sequenceFlow.getTarget() == null
                || !isGateway(sequenceFlow.getTarget())) {
            return targetLayout.centerY();
        }

        List<SequenceFlow> incoming = sortedIncomingSequenceFlows(sequenceFlow, allLayouts);
        if (incoming.size() <= 1) {
            return targetLayout.centerY();
        }

        int incomingIndex = incomingFlowIndex(sequenceFlow, incoming);
        if (incomingIndex < 0) {
            return targetLayout.centerY();
        }

        double targetSpacing = 30;
        double centerOffset = (incoming.size() - 1) / 2.0;
        return targetLayout.centerY() + ((incomingIndex - centerOffset) * targetSpacing);
    }

    private List<SequenceFlow> sortedIncomingSequenceFlows(
            SequenceFlow sequenceFlow,
            Map<String, NodeLayout> allLayouts) {

        if (sequenceFlow == null || sequenceFlow.getTarget() == null) {
            return List.of();
        }

        return sequenceFlow.getTarget().getIncoming().stream()
                .filter(flow -> allLayouts.containsKey(flow.getSource().getId()))
                .filter(flow -> allLayouts.containsKey(flow.getTarget().getId()))
                .sorted(Comparator
                        .comparingDouble((SequenceFlow flow) ->
                                allLayouts.get(flow.getSource().getId()).centerY())
                        .thenComparing(SequenceFlow::getId))
                .toList();
    }

    private int incomingFlowIndex(
            SequenceFlow sequenceFlow,
            List<SequenceFlow> incoming) {

        for (int i = 0; i < incoming.size(); i++) {
            if (incoming.get(i).getId().equals(sequenceFlow.getId())) {
                return i;
            }
        }
        return -1;
    }
/**
 * Checks whether a horizontal segment at height y, spanning [x1, x2], cuts
 * through any node's bounding box. If so, returns a y just above that node's
 * top edge; otherwise returns y unchanged.
 */
private double findClearHorizontalY(
        double x1, double x2, double y,
        Map<String, NodeLayout> allLayouts) {

    double left  = Math.min(x1, x2);
    double right = Math.max(x1, x2);
    double safeY = y;

    for (NodeLayout layout : allLayouts.values()) {
        boolean xOverlap = layout.right() > left && layout.x() < right;
        boolean yOverlap = y > layout.y() && y < layout.y() + layout.height();
        if (xOverlap && yOverlap) {
            safeY = Math.min(safeY, layout.y() - 20);
        }
    }
    return safeY;
}

    /**
     * Routes a backward (loop) edge below all nodes in the bounding region.
     */
    private void routeLoopEdge(
            BpmnModelInstance modelInstance,
            BpmnEdge edge,
            NodeLayout src,
            NodeLayout tgt,
            Map<String, NodeLayout> allLayouts) {

        double regionLeft  = Math.min(src.x(), tgt.x());
        double regionRight = Math.max(src.right(), tgt.right());
        double loopDistance = Math.abs(src.x() - tgt.x());
        boolean useCompactLoopRouting = loopDistance < LOOP_EDGE_COMPACT_THRESHOLD;

        double baseBottom = Math.max(src.y() + src.height(), tgt.y() + tgt.height());
        double maxOverlapBottom = baseBottom;
        boolean hasBlockingOverlap = false;


        for (NodeLayout layout : allLayouts.values()) {

                boolean overlapsBand = layout.x() < regionRight && layout.right() > regionLeft;
            boolean betweenVerticalSpan = layout.y() < baseBottom && layout.y() + layout.height() > Math.min(src.y(), tgt.y());
            if (overlapsBand && betweenVerticalSpan) {
                hasBlockingOverlap = true;
                maxOverlapBottom = Math.max(maxOverlapBottom, layout.y() + layout.height());
            }
        }

         double verticalPadding = useCompactLoopRouting ? 24 : ORTHOGONAL_EDGE_OFFSET + 24;
        double loopBottom = (hasBlockingOverlap ? maxOverlapBottom : baseBottom) + verticalPadding;

        if (useCompactLoopRouting) {
            double compactMax = baseBottom + LOOP_EDGE_MAX_COMPACT_DEPTH;
            loopBottom = Math.min(loopBottom, compactMax);
        } else {
            boolean largeContainer = src.width() > 400 || src.height() > 250 || tgt.width() > 400 || tgt.height() > 250;
            if (largeContainer && hasBlockingOverlap) {
                loopBottom += 24;
            }
        }
        double minDetourGap = useCompactLoopRouting ? 64 : LOOP_EDGE_MIN_DETOUR_X;
        double preferredDetourX = Math.min(src.x(), tgt.right()) - minDetourGap;
        double detourX = Math.max(tgt.right() + 20, preferredDetourX);

        addWaypoint(modelInstance, edge, src.x(), src.centerY());
        addWaypoint(modelInstance, edge, src.x(), loopBottom);
        addWaypoint(modelInstance, edge, detourX, loopBottom);
        addWaypoint(modelInstance, edge, detourX, tgt.centerY());
        addWaypoint(modelInstance, edge, tgt.right(), tgt.centerY());
    }

    /**
     * Routes a same-level or crossing edge below both nodes.
     */
    private void routeAroundEdge(
        BpmnModelInstance modelInstance,
        BpmnEdge edge,
        NodeLayout src,
        NodeLayout tgt,
        Map<String, NodeLayout> allLayouts) {

    double belowY  = Math.max(src.y() + src.height(), tgt.y() + tgt.height())
            + ORTHOGONAL_EDGE_OFFSET + 24;

    double regionLeft  = Math.min(src.right(), tgt.right());
    double regionRight = Math.max(src.x(), tgt.x());

    // FIX: no longer restricted to "isContainer" — any node whose band
    // intersects the path must push the detour further down.
    for (NodeLayout layout : allLayouts.values()) {
        if (layout == src || layout == tgt) continue;
        if (layout.x() < regionRight && layout.right() > regionLeft) {
            belowY = Math.max(belowY, layout.y() + layout.height() + ORTHOGONAL_EDGE_OFFSET);
        }
    }

    double sx = src.right();
    double sy = src.centerY();
    double tx = tgt.x();
    double ty = tgt.centerY();

    addWaypoint(modelInstance, edge, sx, sy);
    addWaypoint(modelInstance, edge, sx, belowY);
    addWaypoint(modelInstance, edge, tx, belowY);
    addWaypoint(modelInstance, edge, tx, ty);
}

    /**
     * Finds a horizontal midpoint X between sx and tx that does not pass through
     * the interior of any container node. If the naive midpoint is inside a container,
     * shifts right past its right edge.
     */
    private double findClearMidpointX(
        double sx, double tx, double sy, double ty,
        Map<String, NodeLayout> allLayouts) {

    double naive = sx + (tx - sx) * 0.5;
    double best = naive;

    for (NodeLayout layout : allLayouts.values()) {
        // FIX: check every node, not just containers — a plain task between
        // source and target on the same horizontal band must be avoided too.
        boolean xOverlap = naive >= layout.x() - 5 && naive <= layout.right() + 5;
        boolean yOverlap = Math.min(sy, ty) < layout.y() + layout.height()
                && Math.max(sy, ty) > layout.y();

        if (xOverlap && yOverlap) {
            best = Math.max(best, layout.right() + 20);
        }
    }
    return best;
}

    private void addWaypoint(BpmnModelInstance modelInstance, BpmnEdge edge, double x, double y) {
        Waypoint waypoint = modelInstance.newInstance(Waypoint.class);
        waypoint.setX(x);
        waypoint.setY(y);
        edge.addChildElement(waypoint);
    }
    private Collaboration ensureCollaboration(BpmnModelInstance modelInstance, Definitions definitions, Process process) {
        Collaboration collaboration = definitions.getChildElementsByType(Collaboration.class).stream().findFirst().orElse(null);
        if (collaboration == null) {
            collaboration = modelInstance.newInstance(Collaboration.class);
            collaboration.setId(process.getId() + "_collaboration");
            definitions.addChildElement(collaboration);
            Participant participant = modelInstance.newInstance(Participant.class);
            participant.setId(process.getId() + "_participant");
            participant.setProcess(process);
            participant.setName(process.getName() == null ? process.getId() : process.getName());
            collaboration.addChildElement(participant);
        }
        return collaboration;
    }





   private void createGenericBpmnEdge(BpmnModelInstance modelInstance, BpmnPlane plane, BaseElement edgeElement, Map<String, NodeLayout> nodeLayouts, Set<String> usedDiIds) {
        String sourceId = null;
        String targetId = null;
        boolean isMessageFlow = false;
        if (edgeElement instanceof MessageFlow flow) {
            sourceId = flow.getSource().getId();
            targetId = flow.getTarget().getId();
            isMessageFlow = true;
        } else if (edgeElement instanceof Association association) {
            sourceId = association.getSource().getId();
            targetId = association.getTarget().getId();
        }
        if (sourceId == null || targetId == null) return;
        NodeLayout sourceLayout = nodeLayouts.get(sourceId);
        NodeLayout targetLayout = nodeLayouts.get(targetId);
        if (sourceLayout == null || targetLayout == null) return;
        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setId(stableDiId("BPMNEdge", edgeElement.getId(), modelInstance, usedDiIds));
        edge.setBpmnElement(edgeElement);
        if (isMessageFlow) {
            routeMessageFlowEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
            plane.addChildElement(edge);
            return;
        }

        boolean isForward = targetLayout.x() >= sourceLayout.right();
        boolean isLoop    = targetLayout.right() <= sourceLayout.x() + 10;

        if (isLoop) {
            routeLoopEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
        } else if (isForward) {
            routeForwardEdge(modelInstance, edge, null, sourceLayout, targetLayout, nodeLayouts);
        } else {
            routeAroundEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
        }
        plane.addChildElement(edge);
    }
    private void routeMessageFlowEdge(
            BpmnModelInstance modelInstance,
            BpmnEdge edge,
            NodeLayout src,
            NodeLayout tgt,
            Map<String, NodeLayout> allLayouts) {

        double sourceTop = src.y();
        double targetTop = tgt.y();
        double sourceX = src.centerX();
        double targetX = tgt.centerX();
        double minTop = Math.min(sourceTop, targetTop);

        double xDistance = Math.abs(sourceX - targetX);
        if (xDistance <= MESSAGE_FLOW_VERTICAL_ALIGN_X) {
            double sideX = Math.max(src.right(), tgt.right()) + 30;
            addWaypoint(modelInstance, edge, sourceX, sourceTop);
            addWaypoint(modelInstance, edge, sideX, sourceTop);
            addWaypoint(modelInstance, edge, sideX, targetTop);
            addWaypoint(modelInstance, edge, targetX, targetTop);
            return;
        }

        double topDelta = Math.abs(sourceTop - targetTop);
        double dynamicClearance = Math.min(56, topDelta + 18);
        double topY = minTop - Math.max(MESSAGE_FLOW_MIN_CLEARANCE, dynamicClearance);

        for (NodeLayout layout : allLayouts.values()) {
           boolean overlapsHorizontalBand = layout.right() > Math.min(sourceX, targetX)&& layout.x() < Math.max(sourceX, targetX);
            boolean intrudesCorridor = layout.y() < minTop && layout.y() + layout.height() > topY;
            if (overlapsHorizontalBand && intrudesCorridor) {
                topY = Math.min(topY, layout.y() - 28);
            }
        } 

        addWaypoint(modelInstance, edge, sourceX, sourceTop);
        addWaypoint(modelInstance, edge, sourceX, topY);
        addWaypoint(modelInstance, edge, targetX, topY);
        addWaypoint(modelInstance, edge, targetX, targetTop);
    }


    // =====================================================
    // NODE SIZE HELPERS
    // =====================================================

    /**
     * Returns the width of a node.
     *
     * FIX: For containers, always reads from globalNodeLayouts (populated in PASS 0)
     *      so that calculateNodeLayouts() in PASS 1 uses the correct final size.
     *      Previously returned hardcoded ADHOC_DEFAULT_WIDTH, causing PASS 1 to
     *      place neighbors too close, and PASS-2 resize was too late.
     */
    private double getNodeWidth(FlowNode node) {
        if (node instanceof SubProcess || node instanceof Transaction) {
            NodeLayout precomputed = globalNodeLayouts.get(node.getId());
            if (precomputed != null) return precomputed.width();
            // Fallback for containers not yet measured (should not occur after PASS 0)
            return SUBPROCESS_COLLAPSED_WIDTH;
        }
        if (node instanceof StartEvent || node instanceof EndEvent) return EVENT_SIZE;
        if (isGateway(node)) return GATEWAY_SIZE;
        return TASK_WIDTH;
    }

    /**
     * Returns the height of a node.
     *
     * FIX: Same fix as getNodeWidth — reads from globalNodeLayouts for containers.
     */
    private double getNodeHeight(FlowNode node) {
        if (node instanceof SubProcess || node instanceof Transaction) {
            NodeLayout precomputed = globalNodeLayouts.get(node.getId());
            if (precomputed != null) return precomputed.height();
            return SUBPROCESS_COLLAPSED_HEIGHT;
        }
        if (node instanceof StartEvent || node instanceof EndEvent) return EVENT_SIZE;
        if (isGateway(node)) return GATEWAY_SIZE;
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
    // CHILD NODE / FLOW COLLECTION HELPERS
    // =====================================================

    private Map<String, FlowNode> collectChildNodes(SubProcess subProcess) {
        Map<String, FlowNode> children = new LinkedHashMap<>();
        for (FlowElement element : subProcess.getFlowElements()) {
            if (element instanceof FlowNode node) {
                children.put(node.getId(), node);
            }
        }
        return children;
    }

    private List<SequenceFlow> collectChildFlows(SubProcess subProcess) {
        List<SequenceFlow> flows = new ArrayList<>();
        for (FlowElement element : subProcess.getFlowElements()) {
            if (element instanceof SequenceFlow flow) {
                flows.add(flow);
            }
        }
        return flows;
    }

    // =====================================================
    // DI ID GENERATION
    // =====================================================

    private String stableDiId(
            String prefix,
            String bpmnElementId,
            BpmnModelInstance modelInstance,
            Set<String> usedIds) {

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

    @SuppressWarnings("unused") // kept for API compatibility
    private AbstractFlowNodeBuilder<?, ?> currentBuilder(AbstractFlowNodeBuilder<?, ?> builder) {
        return builder;
    }

    private void setName(Process process, String name) {
        String normalized = trimToNull(name);
        if (normalized != null) {
            process.setName(normalized);
        }
    }
    private void setName(BaseElement element, String name) {
        String normalized = trimToNull(name);
        if (normalized != null) {
            element.setAttributeValue("name", normalized, true);
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
        if (value == null) return null;
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
         double centerX() {
            return x + (width / 2);
        }

        NodeLayout withY(double newY) {
            return new NodeLayout(x, newY, width, height);
        }
    }
}