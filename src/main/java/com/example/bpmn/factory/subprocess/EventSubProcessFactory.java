package com.example.bpmn.factory.subprocess;

import com.example.bpmn.dto.ElementDTO;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.factory.event.EventFactory;
import com.example.bpmn.factory.flow.FlowFactory;
import com.example.bpmn.factory.gateway.GatewayFactory;
import com.example.bpmn.factory.task.TaskFactory;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.CompensateEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ConditionalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EscalationEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition;
import com.example.bpmn.factory.transaction.TransactionFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating BPMN EventSubProcess elements.
 *
 * ROOT CAUSE OF THE BUG:
 * The Camunda Model API requires that an element be attached to the model tree
 * BEFORE any SequenceFlow can reference it via setSource() / setTarget().
 * The old version called process.addChildElement() AFTER building all children
 * and flows, so FlowFactory threw ModelReferenceException when trying to resolve
 * the auto-generated triggerStart node.
 *
 * FIX — strict 3-step order in both createEventSubProcess() overloads:
 *   1. createShell()        → instantiate the SubProcess (no attachment yet)
 *   2. addChildElement()    → attach to model IMMEDIATELY
 *   3. populateChildren()   → now safe to add children and wire flows
 *
 * This same pattern applies to createNestedSubProcess() inside this factory.
 */
public class EventSubProcessFactory {

    private static final String TRIGGER_START_SUFFIX = "_trigger_start";

    private final TaskFactory taskFactory = new TaskFactory();
    private final GatewayFactory gatewayFactory = new GatewayFactory();
    private final EventFactory eventFactory = new EventFactory();
    private final FlowFactory flowFactory = new FlowFactory();
    private final TransactionFactory transactionFactory = new TransactionFactory();

    // =====================================================
    // EVENT SUBPROCESS FOR MAIN PROCESS
    // =====================================================

    public FlowNode createEventSubProcess(
            BpmnModelInstance modelInstance,
            Process process,
            ElementDTO element
    ) {
        validateElement(element);

        // STEP 1 — create shell only (no children, no flows)
        SubProcess eventSubProcess = createShell(modelInstance, element);

        // STEP 2 — attach to model BEFORE anything else
        process.addChildElement(eventSubProcess);

        // STEP 3 — now safe to populate
        populateChildren(modelInstance, eventSubProcess, element);

        return eventSubProcess;
    }

    // =====================================================
    // EVENT SUBPROCESS FOR PARENT SUBPROCESS (nested)
    // =====================================================

    public FlowNode createEventSubProcess(
            BpmnModelInstance modelInstance,
            SubProcess parentSubProcess,
            ElementDTO element
    ) {
        validateElement(element);

        // STEP 1 — create shell only
        SubProcess eventSubProcess = createShell(modelInstance, element);

        // STEP 2 — attach to parent BEFORE anything else
        parentSubProcess.addChildElement(eventSubProcess);

        // STEP 3 — now safe to populate
        populateChildren(modelInstance, eventSubProcess, element);

        return eventSubProcess;
    }

    // =====================================================
    // STEP 1 — Shell: SubProcess with triggeredByEvent=true, no content yet
    // =====================================================

    private SubProcess createShell(
            BpmnModelInstance modelInstance,
            ElementDTO element
    ) {
        SubProcess eventSubProcess = modelInstance.newInstance(SubProcess.class);
        eventSubProcess.setId(element.getId());
        eventSubProcess.setName(element.getName());
        eventSubProcess.setTriggeredByEvent(true);
        return eventSubProcess;
    }

    // =====================================================
    // STEP 3 — Populate: called only after SubProcess is in the model tree
    // =====================================================

    private void populateChildren(
            BpmnModelInstance modelInstance,
            SubProcess eventSubProcess,
            ElementDTO element
    ) {
        String triggerType = element.getTriggerType();
        if (triggerType == null || triggerType.isBlank()) {
            throw new IllegalArgumentException(
                    "EventSubProcess '" + element.getId() + "' requires a 'triggerType' "
                            + "(message | signal | timer | error | escalation | compensation | conditional)"
            );
        }

        // Auto-generate the trigger startEvent and add it immediately
        // Safe here: the parent SubProcess is already in the model tree
        String triggerStartId = element.getId() + TRIGGER_START_SUFFIX;
        StartEvent triggerStart = createTriggerStartEvent(
                modelInstance, triggerStartId, triggerType, resolveInterrupting(element)
        );
        eventSubProcess.addChildElement(triggerStart);

        // Build all other child nodes
        Map<String, FlowNode> nodesById = new HashMap<>();
        nodesById.put(triggerStartId, triggerStart);

        for (ElementDTO child : safeElements(element)) {
            FlowNode node = createChildNode(modelInstance, eventSubProcess, child);
            nodesById.put(child.getId(), node);
        }

        // Wire flows — all nodes are now in the model, no ModelReferenceException
        for (FlowDTO flow : safeFlows(element)) {
            flowFactory.createSequenceFlow(modelInstance, eventSubProcess, flow, nodesById);
        }
    }

    // =====================================================
    // TRIGGER START EVENT
    // =====================================================

    private StartEvent createTriggerStartEvent(
            BpmnModelInstance modelInstance,
            String id,
            String triggerType,
            boolean interrupting
    ) {
        StartEvent startEvent = modelInstance.newInstance(StartEvent.class);
        startEvent.setId(id);
        startEvent.setName(triggerLabel(triggerType));
        startEvent.setInterrupting(interrupting);
        startEvent.addChildElement(createEventDefinition(modelInstance, triggerType));
        return startEvent;
    }

    private EventDefinition createEventDefinition(
            BpmnModelInstance modelInstance,
            String triggerType
    ) {
        return switch (triggerType.toLowerCase().trim()) {
            case "message"      -> modelInstance.newInstance(MessageEventDefinition.class);
            case "signal"       -> modelInstance.newInstance(SignalEventDefinition.class);
            case "timer"        -> modelInstance.newInstance(TimerEventDefinition.class);
            case "error"        -> modelInstance.newInstance(ErrorEventDefinition.class);
            case "escalation"   -> modelInstance.newInstance(EscalationEventDefinition.class);
            case "compensation" -> modelInstance.newInstance(CompensateEventDefinition.class);
            case "conditional"  -> modelInstance.newInstance(ConditionalEventDefinition.class);
            default -> throw new IllegalArgumentException( "Unsupported EventSubProcess triggerType: '" + triggerType + "'. " + "Supported: message, signal, timer, error, escalation, compensation, conditional" );
        };
    }

    private String triggerLabel(String triggerType) {
        return switch (triggerType.toLowerCase().trim()) {
            case "message"      -> "Message received";
            case "signal"       -> "Signal received";
            case "timer"        -> "Timer triggered";
            case "error"        -> "Error caught";
            case "escalation"   -> "Escalation caught";
            case "compensation" -> "Compensation triggered";
            case "conditional"  -> "Condition met";
            default             -> triggerType;
        };
    }

    // =====================================================
    // CHILD NODE ROUTING
    // =====================================================

    private FlowNode createChildNode(
            BpmnModelInstance modelInstance,
            SubProcess parentSubProcess,
            ElementDTO element
    ) {
        if (element == null) {
            throw new IllegalArgumentException("EventSubProcess child element cannot be null");
        }
        String type = element.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("EventSubProcess child element type is required");
        }

        return switch (type) {
            case "userTask",
                 "serviceTask",
                 "scriptTask",
                 "businessRuleTask",
                 "manualTask",
                 "sendTask",
                 "receiveTask"
                    -> taskFactory.createTask(modelInstance, parentSubProcess, element);

            case "exclusiveGateway",
                 "parallelGateway",
                 "inclusiveGateway",
                 "eventBasedGateway",
                 "complexGateway"
                    -> gatewayFactory.createGateway(modelInstance, parentSubProcess, element);

            case "startEvent",
                 "messageStartEvent",
                 "timerStartEvent",
                 "signalStartEvent",
                 "conditionalStartEvent",
                 "intermediateCatchEvent",
                 "intermediateThrowEvent",
                 "messageEvent",
                 "timerEvent",
                 "signalEvent",
                 "escalationEvent",
                 "compensationEvent",
                 "linkEvent",
                 "endEvent",
                 "terminateEndEvent",
                 "errorEndEvent",
                 "messageEndEvent",
                 "signalEndEvent",
                 "escalationEndEvent"
                    -> eventFactory.createEvent(modelInstance, parentSubProcess, element);

            case "subProcess"
                    -> createNestedSubProcess(modelInstance, parentSubProcess, element);
            case "eventSubProcess"
                    -> createEventSubProcess(modelInstance, parentSubProcess, element);
            case "transaction" ->
                    transactionFactory.createTransaction( modelInstance, parentSubProcess, element );        

            default -> throw new IllegalArgumentException(
                    "Unsupported EventSubProcess child element type: " + type
            );
        };
    }

    /**
     * Nested regular SubProcess inside an EventSubProcess.
     * Same attach-before-populate pattern to prevent ModelReferenceException.
     */
    private FlowNode createNestedSubProcess(
            BpmnModelInstance modelInstance,
            SubProcess parentSubProcess,
            ElementDTO element
    ) {
        SubProcess nested = modelInstance.newInstance(SubProcess.class);
        nested.setId(element.getId());
        nested.setName(element.getName());

        // Attach FIRST
        parentSubProcess.addChildElement(nested);

        // Then populate
        Map<String, FlowNode> nodesById = new HashMap<>();
        for (ElementDTO child : safeElements(element)) {
            FlowNode node = createChildNode(modelInstance, nested, child);
            nodesById.put(child.getId(), node);
        }
        for (FlowDTO flow : safeFlows(element)) {
            flowFactory.createSequenceFlow(modelInstance, nested, flow, nodesById);
        }
        return nested;
    }

    // =====================================================
    // VALIDATION + UTILITY
    // =====================================================

    private void validateElement(ElementDTO element) {
        if (element == null) {
            throw new IllegalArgumentException("EventSubProcess element cannot be null");
        }
        if (element.getId() == null || element.getId().isBlank()) {
            throw new IllegalArgumentException("EventSubProcess element id is required");
        }
    }

    private boolean resolveInterrupting(ElementDTO element) {
        Boolean interrupting = element.getInterrupting();
        return interrupting == null || interrupting;
    }

    private List<ElementDTO> safeElements(ElementDTO element) {
        return element.getElements() == null ? Collections.emptyList() : element.getElements();
    }

    private List<FlowDTO> safeFlows(ElementDTO element) {
        return element.getFlows() == null ? Collections.emptyList() : element.getFlows();
    }
}