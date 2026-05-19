package com.example.bpmn.factory.event;
 
import com.example.bpmn.dto.ElementDTO;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.CancelEventDefinition;
import org.camunda.bpm.model.bpmn.instance.CompensateEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ConditionalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EscalationEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.IntermediateThrowEvent;
import org.camunda.bpm.model.bpmn.instance.LinkEventDefinition;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.TerminateEventDefinition;
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition;
import org.camunda.bpm.model.bpmn.instance.Transaction;
 
public class EventFactory {
 
    // =====================================================
    // PUBLIC — MAIN PROCESS
    // =====================================================
 
    public FlowNode createEvent(
            BpmnModelInstance modelInstance,
            Process process,
            ElementDTO element
    ) {
        return createEventInternal(modelInstance, process, null, null, element);
    }
 
    // =====================================================
    // PUBLIC — SUBPROCESS
    // =====================================================
 
    public FlowNode createEvent(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            ElementDTO element
    ) {
        return createEventInternal(modelInstance, null, subProcess, null, element);
    }
 
    // =====================================================
    // PUBLIC — TRANSACTION (new)
    // =====================================================
 
    public FlowNode createEvent(
            BpmnModelInstance modelInstance,
            Transaction transaction,
            ElementDTO element
    ) {
        return createEventInternal(modelInstance, null, null, transaction, element);
    }
 
    // =====================================================
    // CORE — shared by all three overloads
    // =====================================================
 
    private FlowNode createEventInternal(
            BpmnModelInstance modelInstance,
            Process process,
            SubProcess subProcess,
            Transaction transaction,
            ElementDTO element
    ) {
        String type = element.getType();
        String id   = element.getId();
        String name = element.getName();
 
        return switch (type) {
 
            // ── Start Events ──────────────────────────────────────────
            case "startEvent" ->
                    createNode(modelInstance, process, subProcess, transaction, id, name, StartEvent.class);
 
            case "messageStartEvent" ->
                    createStartEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, MessageEventDefinition.class);
 
            case "timerStartEvent" ->
                    createStartEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, TimerEventDefinition.class);
 
            case "signalStartEvent" ->
                    createStartEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, SignalEventDefinition.class);
 
            case "conditionalStartEvent" ->
                    createStartEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, ConditionalEventDefinition.class);
 
            // ── Intermediate Events ───────────────────────────────────
            case "intermediateCatchEvent" ->
                    createNode(modelInstance, process, subProcess, transaction, id, name, IntermediateCatchEvent.class);
 
            case "intermediateThrowEvent" ->
                    createNode(modelInstance, process, subProcess, transaction, id, name, IntermediateThrowEvent.class);
 
            case "messageEvent" ->
                    createIntermediateCatchEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, MessageEventDefinition.class);
 
            case "timerEvent" ->
                    createIntermediateCatchEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, TimerEventDefinition.class);
 
            case "signalEvent" ->
                    createIntermediateCatchEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, SignalEventDefinition.class);
 
            case "escalationEvent" ->
                    createIntermediateThrowEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, EscalationEventDefinition.class);
 
            case "compensationEvent" ->
                    createIntermediateThrowEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, CompensateEventDefinition.class);
 
            case "linkEvent" ->
                    createIntermediateThrowEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, LinkEventDefinition.class);
 
            // ── End Events ────────────────────────────────────────────
            case "endEvent" ->
                    createNode(modelInstance, process, subProcess, transaction, id, name, EndEvent.class);
 
            case "terminateEndEvent" ->
                    createEndEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, TerminateEventDefinition.class);
 
            case "errorEndEvent" ->
                    createEndEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, ErrorEventDefinition.class);
 
            case "messageEndEvent" ->
                    createEndEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, MessageEventDefinition.class);
 
            case "signalEndEvent" ->
                    createEndEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, SignalEventDefinition.class);
 
            case "escalationEndEvent" ->
                    createEndEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, EscalationEventDefinition.class);
 
            // ── Cancel End Event (specific to Transaction SubProcess) ─
            case "cancelEndEvent" ->
                    createEndEventWithDefinition(modelInstance, process, subProcess, transaction,
                            id, name, CancelEventDefinition.class);
 
            default -> throw new IllegalArgumentException("Unsupported event type: " + type);
        };
    }
 
    // =====================================================
    // NODE CREATION — base method, all parent types
    // =====================================================
 
    private <T extends FlowNode> T createNode(
            BpmnModelInstance modelInstance,
            Process process,
            SubProcess subProcess,
            Transaction transaction,
            String id,
            String name,
            Class<T> clazz
    ) {
        T node = modelInstance.newInstance(clazz);
        node.setId(id);
        node.setName(name);
 
        if (process != null)     { process.addChildElement(node); }
        if (subProcess != null)  { subProcess.addChildElement(node); }
        if (transaction != null) { transaction.addChildElement(node); }
 
        return node;
    }
 
    // =====================================================
    // START EVENTS WITH DEFINITION
    // =====================================================
 
    private <T extends EventDefinition> StartEvent createStartEventWithDefinition(
            BpmnModelInstance modelInstance,
            Process process, SubProcess subProcess, Transaction transaction,
            String id, String name, Class<T> definitionClass
    ) {
        StartEvent event = createNode(modelInstance, process, subProcess, transaction,
                id, name, StartEvent.class);
        event.addChildElement(modelInstance.newInstance(definitionClass));
        return event;
    }
 
    // =====================================================
    // INTERMEDIATE CATCH EVENTS WITH DEFINITION
    // =====================================================
 
    private <T extends EventDefinition> IntermediateCatchEvent createIntermediateCatchEventWithDefinition(
            BpmnModelInstance modelInstance,
            Process process, SubProcess subProcess, Transaction transaction,
            String id, String name, Class<T> definitionClass
    ) {
        IntermediateCatchEvent event = createNode(modelInstance, process, subProcess, transaction,
                id, name, IntermediateCatchEvent.class);
        event.addChildElement(modelInstance.newInstance(definitionClass));
        return event;
    }
 
    // =====================================================
    // INTERMEDIATE THROW EVENTS WITH DEFINITION
    // =====================================================
 
    private <T extends EventDefinition> IntermediateThrowEvent createIntermediateThrowEventWithDefinition(
            BpmnModelInstance modelInstance,
            Process process, SubProcess subProcess, Transaction transaction,
            String id, String name, Class<T> definitionClass
    ) {
        IntermediateThrowEvent event = createNode(modelInstance, process, subProcess, transaction,
                id, name, IntermediateThrowEvent.class);
        event.addChildElement(modelInstance.newInstance(definitionClass));
        return event;
    }
 
    // =====================================================
    // END EVENTS WITH DEFINITION
    // =====================================================
 
    private <T extends EventDefinition> EndEvent createEndEventWithDefinition(
            BpmnModelInstance modelInstance,
            Process process, SubProcess subProcess, Transaction transaction,
            String id, String name, Class<T> definitionClass
    ) {
        EndEvent event = createNode(modelInstance, process, subProcess, transaction,
                id, name, EndEvent.class);
        event.addChildElement(modelInstance.newInstance(definitionClass));
        return event;
    }
}