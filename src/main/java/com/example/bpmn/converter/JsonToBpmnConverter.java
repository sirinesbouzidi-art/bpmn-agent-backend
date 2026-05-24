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
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
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
    private static final double HORIZONTAL_GAP                 = 260;
    private static final double MIN_VERTICAL_GAP               = 150;
    private static final double LANE_PADDING                   = 70;
    private static final double EVENT_SIZE                     = 36;
    private static final double GATEWAY_SIZE                   = 50;
    private static final double TASK_WIDTH                     = 120;
    private static final double TASK_HEIGHT                    = 80;
    private static final double SUBPROCESS_COLLAPSED_WIDTH     = 180;
    private static final double SUBPROCESS_COLLAPSED_HEIGHT    = 110;
    private static final double ADHOC_DEFAULT_WIDTH            = 500;
    private static final double ADHOC_DEFAULT_HEIGHT           = 320;
    private static final double ORTHOGONAL_EDGE_OFFSET         = 30;
    private static final double SUBPROCESS_TOP_PADDING         = 40;
    private static final double SUBPROCESS_BOTTOM_PADDING      = 40;
    private static final double SUBPROCESS_LEFT_PADDING        = 52;
    private static final double SUBPROCESS_RIGHT_PADDING       = 90;
    private static final double NESTED_SUBPROCESS_MIN_GAP      = 42;
    private static final double EDGE_VERTICAL_SPLIT_THRESHOLD  = 140;
    private static final double LOOP_EDGE_MIN_DETOUR_X         = 120;
    private static final double SUBPROCESS_TITLE_CLEARANCE     = 30;
    private static final double CONTAINER_RIGHT_NEIGHBOR_GAP   = 60;
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
                case "dataInputAssociation" -> nonSequenceFlows.add(flowFactory.createDataInputAssociation(modelInstance, flow, elementsById));
                case "dataOutputAssociation" -> nonSequenceFlows.add(flowFactory.createDataOutputAssociation(modelInstance, flow, elementsById));
                
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

            if (adHocSubProcessIds.contains(subProcess.getId())) {
                globalNodeLayouts.put(subProcess.getId(), measureAdHocContainer(children));
            } else {
                globalNodeLayouts.put(subProcess.getId(),
                        measureExpandedSubProcessContainer(children, childFlows));
            }
        }
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

        // Enforce minimum expanded size (at least twice the collapsed size)
        width  = Math.max(width,  SUBPROCESS_COLLAPSED_WIDTH  * 2.0);
        height = Math.max(height, SUBPROCESS_COLLAPSED_HEIGHT * 2.0);

        return new NodeLayout(0, 0, width, height); // x,y are placeholders; PASS 1 sets them
    }

    /**
     * Measures an AdHoc subprocess using a 2-column grid layout in local coordinate space.
     */
   private NodeLayout measureAdHocContainer(Map<String, FlowNode> children) {
    int count = children.size();

    int cols = Math.min(2, Math.max(1, count));
    int rows = (int) Math.ceil((double) count / cols);

    double taskGapX = 80;
    double taskGapY = 70;

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
    width = Math.max(width, 420);
    height = Math.max(height, 220);

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

                // Push all nodes in all subsequent levels that start before requiredClearance
                for (int nextIdx = levelIdx + 1; nextIdx < sortedLevels.size(); nextIdx++) {
                    int nextLevel = sortedLevels.get(nextIdx);
                    for (FlowNode neighbor : nodesByLevel.get(nextLevel)) {
                        NodeLayout neighborLayout = nodeLayouts.get(neighbor.getId());
                        if (neighborLayout == null) continue;

                        if (neighborLayout.x() < requiredClearance) {
                            double shift = requiredClearance - neighborLayout.x();
                            nodeLayouts.put(neighbor.getId(), new NodeLayout(
                                    neighborLayout.x() + shift,
                                    neighborLayout.y(),
                                    neighborLayout.width(),
                                    neighborLayout.height()));
                        }
                    }
                }
            }
        }
        // Re-space levels after container expansion
for (int levelIdx = 0; levelIdx < sortedLevels.size() - 1; levelIdx++) {

    int currentLevel = sortedLevels.get(levelIdx);
    int nextLevel = sortedLevels.get(levelIdx + 1);

    double maxRight = 0;

    for (FlowNode node : nodesByLevel.get(currentLevel)) {
        NodeLayout layout = nodeLayouts.get(node.getId());

        if (layout != null) {
            maxRight = Math.max(maxRight, layout.right());
        }
    }

    double requiredX = maxRight + HORIZONTAL_GAP;

    for (FlowNode node : nodesByLevel.get(nextLevel)) {

        NodeLayout layout = nodeLayouts.get(node.getId());

        if (layout != null && layout.x() < requiredX) {

            nodeLayouts.put(
                node.getId(),
                new NodeLayout(
                    requiredX,
                    layout.y(),
                    layout.width(),
                    layout.height()
                )
            );
        }
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
        for (Map.Entry<Integer, List<FlowNode>> entry : nodesByLevel.entrySet()) {
            int level            = entry.getKey();
            List<FlowNode> levelNodes = entry.getValue();
            double verticalGap   = verticalGapFor(levelNodes);
            double totalHeight   = totalLevelHeight(levelNodes, verticalGap);
            double y             = Math.max(START_Y, centerY - (totalHeight / 2));

            for (FlowNode node : levelNodes) {
                double width  = getNodeWidth(node);
                double height = getNodeHeight(node);
                nodeLayouts.put(node.getId(),
                        new NodeLayout(startX + (level * HORIZONTAL_GAP), y, width, height));
                y += height + verticalGap;
            }
        }
        return balanceGatewayBranches(nodeLayouts, nodesByLevel, graph);
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
        double rowSpacing  = Math.max(MIN_VERTICAL_GAP, TASK_HEIGHT + 70);

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

        for (String nodeId : nodeIds) {
            levels.put(nodeId, 0);
            int inCount = graph.incoming().getOrDefault(nodeId, List.of()).size();
            indegree.put(nodeId, inCount);
            if (inCount == 0) ready.add(nodeId);
        }

        while (!ready.isEmpty()) {
            String srcId = ready.remove();
            for (String tgtId : graph.outgoing().getOrDefault(srcId, List.of())) {
                levels.put(tgtId, Math.max(
                        levels.getOrDefault(tgtId, 0),
                        levels.getOrDefault(srcId, 0) + 1));
                int newIndegree = indegree.merge(tgtId, -1, Integer::sum);
                if (newIndegree == 0) ready.add(tgtId);
            }
        }

        // Extra passes for cycles
        for (int i = 0; i < nodeIds.size(); i++) {
            boolean changed = false;
            for (Map.Entry<String, List<String>> entry : graph.outgoing().entrySet()) {
                for (String tgtId : entry.getValue()) {
                    int candidate = levels.getOrDefault(entry.getKey(), 0) + 1;
                    if (candidate > levels.getOrDefault(tgtId, 0) && candidate <= nodeIds.size()) {
                        levels.put(tgtId, candidate);
                        changed = true;
                    }
                }
            }
            if (!changed) break;
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
                double branchGap      = Math.max(MIN_VERTICAL_GAP,
                        verticalGapForLayouts(nextLevelTargets, balanced));
                double firstCenterY   = gatewayCenterY
                        - ((nextLevelTargets.size() - 1) * branchGap / 2);

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
        return Math.max(MIN_VERTICAL_GAP, (hasGateway ? 190 : 160) + (nodes.size() * 8));
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
                    artifactLayouts.put(target.getId(), new NodeLayout(src.right() + 60, src.y() - 20, 140, 50));
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
            routeForwardEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
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
            NodeLayout src,
            NodeLayout tgt,
            Map<String, NodeLayout> allLayouts) {

        double sx = src.right();
        double sy = src.centerY();
        double tx = tgt.x();
        double ty = tgt.centerY();

        double horizontalDist = tx - sx;
        double verticalDist   = Math.abs(ty - sy);

        double midX = findClearMidpointX(sx, tx, sy, ty, allLayouts);

        addWaypoint(modelInstance, edge, sx, sy);

        if (verticalDist <= EDGE_VERTICAL_SPLIT_THRESHOLD) {
            addWaypoint(modelInstance, edge, midX, sy);
            addWaypoint(modelInstance, edge, midX, ty);
        } else {
            double splitX = sx + Math.max(40, horizontalDist * 0.33);
            // Use the larger of the computed split and the clear midpoint
            double safeX  = Math.max(splitX, midX);
            addWaypoint(modelInstance, edge, safeX, sy);
            addWaypoint(modelInstance, edge, safeX, ty);
        }

        addWaypoint(modelInstance, edge, tx, ty);
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

        // Find the lowest bottom edge of any node in the bounding region
        double loopBottom = Math.max(src.y() + src.height(), tgt.y() + tgt.height());
        for (NodeLayout layout : allLayouts.values()) {
            if (layout.x() < regionRight && layout.right() > regionLeft) {
                loopBottom = Math.max(loopBottom, layout.y() + layout.height());
            }
        }

        boolean largeContainer = src.width() > 400 || src.height() > 250
                || tgt.width() > 400 || tgt.height() > 250;
        loopBottom += largeContainer ? 120 : ORTHOGONAL_EDGE_OFFSET + 24;

        double detourX = (Math.abs(tgt.right() - src.x()) < LOOP_EDGE_MIN_DETOUR_X)
                ? src.x() - LOOP_EDGE_MIN_DETOUR_X
                : tgt.right();

        addWaypoint(modelInstance, edge, src.x(),    src.centerY());
        addWaypoint(modelInstance, edge, src.x(),    loopBottom);
        addWaypoint(modelInstance, edge, detourX,    loopBottom);
        addWaypoint(modelInstance, edge, detourX,    tgt.centerY());
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

        // Push below any container that overlaps the region between source and target
        for (NodeLayout layout : allLayouts.values()) {
            boolean isContainer = layout.width() > TASK_WIDTH * 1.5;
            if (!isContainer) continue;
            double regionLeft  = Math.min(src.right(), tgt.right());
            double regionRight = Math.max(src.x(), tgt.x());
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

        for (NodeLayout layout : allLayouts.values()) {
            boolean isContainer = layout.width() > TASK_WIDTH * 1.5;
            if (!isContainer) continue;

            boolean xOverlap = naive >= layout.x() && naive <= layout.right();
            boolean yOverlap = Math.min(sy, ty) < layout.y() + layout.height()
                    && Math.max(sy, ty) > layout.y();

            if (xOverlap && yOverlap) {
                // Route past the container's right edge
                return layout.right() + 20;
            }
        }
        return naive;
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
        if (edgeElement instanceof MessageFlow flow) {
            sourceId = flow.getSource().getId();
            targetId = flow.getTarget().getId();
        } else if (edgeElement instanceof Association association) {
            sourceId = association.getSource().getId();
            targetId = association.getTarget().getId();
        } else if (edgeElement instanceof DataInputAssociation inputAssociation) {
            if (!inputAssociation.getSources().isEmpty()) sourceId = inputAssociation.getSources().iterator().next().getId();
            if (inputAssociation.getParentElement() instanceof BaseElement parent) targetId = parent.getId();
        } else if (edgeElement instanceof DataOutputAssociation outputAssociation) {
            if (outputAssociation.getParentElement() instanceof BaseElement parent) sourceId = parent.getId();
            if (outputAssociation.getTarget() != null) targetId = outputAssociation.getTarget().getId();
        }
        if (sourceId == null || targetId == null) return;
        NodeLayout sourceLayout = nodeLayouts.get(sourceId);
        NodeLayout targetLayout = nodeLayouts.get(targetId);
        if (sourceLayout == null || targetLayout == null) return;
        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setId(stableDiId("BPMNEdge", edgeElement.getId(), modelInstance, usedDiIds));
        edge.setBpmnElement(edgeElement);

        boolean isForward = targetLayout.x() >= sourceLayout.right();
        boolean isLoop    = targetLayout.right() <= sourceLayout.x() + 10;

        if (isLoop) {
            routeLoopEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
        } else if (isForward) {
            routeForwardEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
        } else {
            routeAroundEdge(modelInstance, edge, sourceLayout, targetLayout, nodeLayouts);
        }
        plane.addChildElement(edge);
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

        NodeLayout withY(double newY) {
            return new NodeLayout(x, newY, width, height);
        }
    }
}