package com.example.bpmn.factory.subprocess;

import com.example.bpmn.dto.ElementDTO;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.factory.event.EventFactory;
import com.example.bpmn.factory.flow.FlowFactory;
import com.example.bpmn.factory.gateway.GatewayFactory;
import com.example.bpmn.factory.task.TaskFactory;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import com.example.bpmn.factory.transaction.TransactionFactory;
import com.example.bpmn.factory.subprocess.AdHocSubProcessFactory;
import com.example.bpmn.factory.subprocess.CallActivityFactory;
import com.example.bpmn.factory.subprocess.EventSubProcessFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubProcessFactory {

    private final TaskFactory taskFactory = new TaskFactory();
    private final GatewayFactory gatewayFactory = new GatewayFactory();
    private final EventFactory eventFactory = new EventFactory();
    private final FlowFactory flowFactory = new FlowFactory();
    private final CallActivityFactory callActivityFactory = new CallActivityFactory();
    private final EventSubProcessFactory eventSubProcessFactory = new EventSubProcessFactory();
    private final TransactionFactory transactionFactory = new TransactionFactory();
    private final AdHocSubProcessFactory adHocSubProcessFactory = new AdHocSubProcessFactory();

    // =====================================================
    // SUBPROCESS FOR MAIN PROCESS
    // =====================================================

    public FlowNode createSubProcess(
            BpmnModelInstance modelInstance,
            org.camunda.bpm.model.bpmn.instance.Process process,
            ElementDTO element
    ) {
        SubProcess subProcess = modelInstance.newInstance(SubProcess.class);
        subProcess.setId(element.getId());
        subProcess.setName(element.getName());

        process.addChildElement(subProcess);

        Map<String, FlowNode> nodesById = new HashMap<>();

        for (ElementDTO child : safeElements(element)) {
            // FIX 1: was createNestedChildNode(modelInstance, nested, child)
            //        "nested" variable does not exist in this scope.
            //        Correct call: createChildNode(modelInstance, subProcess, child)
            FlowNode node = createChildNode(modelInstance, subProcess, child);
            nodesById.put(child.getId(), node);
        }

        // FIX 2: was element.getFlows() — throws NPE when flows is null.
        //        Use safeFlows(element) consistent with the nested overload below.
        for (FlowDTO flow : safeFlows(element)) {
            flowFactory.createSequenceFlow(modelInstance, subProcess, flow, nodesById);
        }

        return subProcess;
    }

    // =====================================================
    // SUBPROCESS FOR PARENT SUBPROCESS (nested)
    // =====================================================

    public FlowNode createSubProcess(
            BpmnModelInstance modelInstance,
            SubProcess parentSubProcess,
            ElementDTO element
    ) {
        SubProcess subProcess = modelInstance.newInstance(SubProcess.class);
        subProcess.setId(element.getId());
        subProcess.setName(element.getName());

        parentSubProcess.addChildElement(subProcess);

        Map<String, FlowNode> nodesById = new HashMap<>();

        for (ElementDTO child : safeElements(element)) {
            FlowNode node = createChildNode(modelInstance, subProcess, child);
            nodesById.put(child.getId(), node);
        }

        for (FlowDTO flow : safeFlows(element)) {
            flowFactory.createSequenceFlow(modelInstance, subProcess, flow, nodesById);
        }

        return subProcess;
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

        return switch (type) {
            case "userTask",
                 "serviceTask",
                 "scriptTask",
                 "businessRuleTask",
                 "manualTask",
                 "sendTask",
                 "receiveTask"
                    -> taskFactory.createTask(modelInstance, subProcess, element);

            case "exclusiveGateway",
                 "parallelGateway",
                 "inclusiveGateway",
                 "eventBasedGateway",
                 "complexGateway"
                    -> gatewayFactory.createGateway(modelInstance, subProcess, element);

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
                 "endEvent",
                 "terminateEndEvent",
                 "errorEndEvent",
                 "messageEndEvent",
                 "signalEndEvent",
                 "escalationEndEvent",
                 "escalationEvent",
                 "compensationEvent",
                 "linkEvent"
                    -> eventFactory.createEvent(modelInstance, subProcess, element);

            case "subProcess"
                    -> createSubProcess(modelInstance, subProcess, element);

            case "callActivity"
                    -> callActivityFactory.createCallActivity(modelInstance, subProcess, element);

            case "eventSubProcess"
                    -> eventSubProcessFactory.createEventSubProcess(modelInstance, subProcess, element);

            case "transaction"
                    -> transactionFactory.createTransaction(modelInstance, subProcess, element);

            case "adHocSubProcess"
                    -> adHocSubProcessFactory.createAdHocSubProcess(modelInstance, subProcess, element);

            default -> throw new IllegalArgumentException(
                    "Unsupported subprocess element type: " + type);
        };
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

    public FlowNode createChildNodePublic(
        BpmnModelInstance modelInstance,
        SubProcess parentSubProcess,
        ElementDTO element) {
    return createChildNode(modelInstance, parentSubProcess, element);
}
}