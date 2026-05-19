package com.example.bpmn.factory.subprocess;

import com.example.bpmn.dto.ElementDTO;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.factory.event.EventFactory;
import com.example.bpmn.factory.flow.FlowFactory;
import com.example.bpmn.factory.gateway.GatewayFactory;
import com.example.bpmn.factory.task.TaskFactory;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.Transaction;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdHocSubProcessFactory {

    private final TaskFactory taskFactory = new TaskFactory();
    private final GatewayFactory gatewayFactory = new GatewayFactory();
    private final EventFactory eventFactory = new EventFactory();
    private final FlowFactory flowFactory = new FlowFactory();

    // =====================================================
    // MAIN PROCESS
    // =====================================================

    public FlowNode createAdHocSubProcess(
            BpmnModelInstance modelInstance,
            Process process,
            ElementDTO element
    ) {
        validateAdHocSubProcess(element);
        SubProcess subProcess = createShell(modelInstance, element);
        process.addChildElement(subProcess);
        populate(modelInstance, subProcess, element);
        return subProcess;
    }

    // =====================================================
    // PARENT SUBPROCESS
    // =====================================================

    public FlowNode createAdHocSubProcess(
            BpmnModelInstance modelInstance,
            SubProcess parentSubProcess,
            ElementDTO element
    ) {
        validateAdHocSubProcess(element);
        SubProcess subProcess = createShell(modelInstance, element);
        parentSubProcess.addChildElement(subProcess);
        populate(modelInstance, subProcess, element);
        return subProcess;
    }

    // =====================================================
    // PARENT TRANSACTION
    // FIX: this overload was missing — TransactionFactory calls
    //      createAdHocSubProcess(modelInstance, Transaction, element)
    //      but no such signature existed, causing the compilation error.
    // =====================================================

    public FlowNode createAdHocSubProcess(
            BpmnModelInstance modelInstance,
            Transaction parentTransaction,
            ElementDTO element
    ) {
        validateAdHocSubProcess(element);
        SubProcess subProcess = createShell(modelInstance, element);
        parentTransaction.addChildElement(subProcess);
        populate(modelInstance, subProcess, element);
        return subProcess;
    }

    // =====================================================
    // CREATE SHELL
    // =====================================================

    private SubProcess createShell(
            BpmnModelInstance modelInstance,
            ElementDTO element
    ) {
        SubProcess subProcess = modelInstance.newInstance(SubProcess.class);
        subProcess.setId(element.getId());
        subProcess.setName(element.getName());
        return subProcess;
    }

    // =====================================================
    // POPULATE
    // =====================================================

    private void populate(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            ElementDTO element
    ) {
        Map<String, FlowNode> nodesById = new HashMap<>();

        for (ElementDTO child : safeElements(element)) {
            FlowNode node = createChildNode(modelInstance, subProcess, child);
            nodesById.put(child.getId(), node);
        }

        for (FlowDTO flow : safeFlows(element)) {
            flowFactory.createSequenceFlow(modelInstance, subProcess, flow, nodesById);
        }
    }

    // =====================================================
    // CHILD NODE ROUTING
    // =====================================================

    private FlowNode createChildNode(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            ElementDTO element
    ) {
        String type = element.getType();

        // =====================================================
        // BPMN CONSTRAINT:
        // ADHOC SUBPROCESS CANNOT CONTAIN START EVENTS OR END EVENTS
        // =====================================================

        if ("startEvent".equals(type)
                || "endEvent".equals(type)
                || type.endsWith("StartEvent")
                || type.endsWith("EndEvent")) {
            throw new IllegalArgumentException(
                    "AdHocSubProcess cannot contain start/end events");
        }

        return switch (type) {

            // TASKS
            case "userTask",
                 "serviceTask",
                 "scriptTask",
                 "businessRuleTask",
                 "manualTask",
                 "sendTask",
                 "receiveTask"
                    -> taskFactory.createTask(modelInstance, subProcess, element);

            // GATEWAYS
            case "exclusiveGateway",
                 "parallelGateway",
                 "inclusiveGateway",
                 "eventBasedGateway",
                 "complexGateway"
                    -> gatewayFactory.createGateway(modelInstance, subProcess, element);

            // INTERMEDIATE EVENTS ONLY
            case "intermediateCatchEvent",
                 "intermediateThrowEvent",
                 "messageEvent",
                 "timerEvent",
                 "signalEvent",
                 "escalationEvent",
                 "compensationEvent",
                 "linkEvent"
                    -> eventFactory.createEvent(modelInstance, subProcess, element);

            // NORMAL SUBPROCESS
            case "subProcess"
                    -> new SubProcessFactory().createSubProcess(modelInstance, subProcess, element);

            // NESTED ADHOC SUBPROCESS
            case "adHocSubProcess"
                    -> createAdHocSubProcess(modelInstance, subProcess, element);

            default -> throw new IllegalArgumentException(
                    "Unsupported AdHoc child type: " + type);
        };
    }

    // =====================================================
    // VALIDATION
    // =====================================================

    private void validateAdHocSubProcess(ElementDTO element) {
        // BPMN RULE: MUST CONTAIN AT LEAST ONE ACTIVITY
        if (safeElements(element).isEmpty()) {
            throw new IllegalArgumentException(
                    "AdHocSubProcess must contain at least one activity");
        }
    }

    // =====================================================
    // UTILITIES
    // =====================================================

    private List<ElementDTO> safeElements(ElementDTO element) {
        return element.getElements() == null ? Collections.emptyList() : element.getElements();
    }

    private List<FlowDTO> safeFlows(ElementDTO element) {
        return element.getFlows() == null ? Collections.emptyList() : element.getFlows();
    }
}