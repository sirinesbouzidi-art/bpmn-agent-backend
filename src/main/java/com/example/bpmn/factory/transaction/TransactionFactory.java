package com.example.bpmn.factory.transaction;

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
import com.example.bpmn.factory.subprocess.AdHocSubProcessFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TransactionFactory {

    private final TaskFactory taskFactory = new TaskFactory();
    private final GatewayFactory gatewayFactory = new GatewayFactory();
    private final EventFactory eventFactory = new EventFactory();
    private final FlowFactory flowFactory = new FlowFactory();
    private final AdHocSubProcessFactory adHocSubProcessFactory = new AdHocSubProcessFactory();

    // =====================================================
    // TRANSACTION FOR MAIN PROCESS
    // =====================================================

    public FlowNode createTransaction(
            BpmnModelInstance modelInstance,
            Process process,
            ElementDTO element
    ) {
        validateElement(element);

        // STEP 1 — create shell only
        Transaction transaction = createShell(modelInstance, element);

        // STEP 2 — attach to model BEFORE any child or flow
        process.addChildElement(transaction);

        // STEP 3 — now safe to populate
        populateChildren(modelInstance, transaction, element);

        return transaction;
    }

    // =====================================================
    // TRANSACTION FOR PARENT SUBPROCESS (nested)
    // =====================================================

    public FlowNode createTransaction(
            BpmnModelInstance modelInstance,
            SubProcess parentSubProcess,
            ElementDTO element
    ) {
        validateElement(element);

        // STEP 1 — create shell only
        Transaction transaction = createShell(modelInstance, element);

        // STEP 2 — attach to parent BEFORE any child or flow
        parentSubProcess.addChildElement(transaction);

        // STEP 3 — now safe to populate
        populateChildren(modelInstance, transaction, element);

        return transaction;
    }

    // =====================================================
    // TRANSACTION FOR PARENT TRANSACTION (nested transaction)
    // =====================================================

    public FlowNode createTransaction(
            BpmnModelInstance modelInstance,
            Transaction parentTransaction,
            ElementDTO element
    ) {
        validateElement(element);

        // STEP 1 — create shell only
        Transaction transaction = createShell(modelInstance, element);

        // STEP 2 — attach to parent BEFORE any child or flow
        parentTransaction.addChildElement(transaction);

        // STEP 3 — now safe to populate
        populateChildren(modelInstance, transaction, element);

        return transaction;
    }

    // =====================================================
    // STEP 1 — Shell: Transaction element, no content yet
    // =====================================================

    private Transaction createShell(
            BpmnModelInstance modelInstance,
            ElementDTO element
    ) {
        Transaction transaction = modelInstance.newInstance(Transaction.class);
        transaction.setId(element.getId());
        transaction.setName(element.getName());
        return transaction;
    }

    // =====================================================
    // STEP 3 — Populate: called only after Transaction is in the model tree
    // =====================================================

    private void populateChildren(
            BpmnModelInstance modelInstance,
            Transaction transaction,
            ElementDTO element
    ) {
        // Build all child nodes first
        Map<String, FlowNode> nodesById = new HashMap<>();

        for (ElementDTO child : safeElements(element)) {
            FlowNode node = createChildNode(modelInstance, transaction, child);
            nodesById.put(child.getId(), node);
        }

        // Wire flows — all nodes are now in the model
        for (FlowDTO flow : safeFlows(element)) {
            flowFactory.createSequenceFlow(modelInstance, transaction, flow, nodesById);
        }
    }

    // =====================================================
    // CHILD NODE ROUTING
    // =====================================================

    private FlowNode createChildNode(
            BpmnModelInstance modelInstance,
            Transaction parentTransaction,
            ElementDTO element
    ) {
        if (element == null) {
            throw new IllegalArgumentException("Transaction child element cannot be null");
        }
        String type = element.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Transaction child element type is required");
        }

        return switch (type) {
            case "userTask",
                 "serviceTask",
                 "scriptTask",
                 "businessRuleTask",
                 "manualTask",
                 "sendTask",
                 "receiveTask"
                    -> taskFactory.createTask(modelInstance, parentTransaction, element);

            case "exclusiveGateway",
                 "parallelGateway",
                 "inclusiveGateway",
                 "eventBasedGateway",
                 "complexGateway"
                    -> gatewayFactory.createGateway(modelInstance, parentTransaction, element);

            // All event types including cancelEndEvent (key for transactions)
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
                 "escalationEndEvent",
                 "cancelEndEvent"           // ← specific to transactions
                    -> eventFactory.createEvent(modelInstance, parentTransaction, element);

            // Nested regular subProcess inside a transaction
            case "subProcess"
                    -> createNestedSubProcess(modelInstance, parentTransaction, element);

            // Nested transaction inside a transaction
            case "transaction"
                    -> createTransaction(modelInstance, parentTransaction, element);
             // Nested adHocSubProcess inside a transaction
            case "adHocSubProcess"
                    -> adHocSubProcessFactory.createAdHocSubProcess(modelInstance, parentTransaction, element);        

            default -> throw new IllegalArgumentException(
                    "Unsupported Transaction child element type: " + type
            );
        };
    }

    /**
     * Creates a regular SubProcess nested inside a Transaction.
     * Uses the same attach-before-populate pattern.
     */
    private FlowNode createNestedSubProcess(
        BpmnModelInstance modelInstance,
        Transaction parentTransaction,
        ElementDTO element
) {
    SubProcess nested = modelInstance.newInstance(SubProcess.class);
    nested.setId(element.getId());
    nested.setName(element.getName());

    parentTransaction.addChildElement(nested); // attach first

    Map<String, FlowNode> nodesById = new HashMap<>();
    for (ElementDTO child : safeElements(element)) {
        FlowNode node = createNestedSubProcessChild(modelInstance, nested, child); // ← FIXED
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
            throw new IllegalArgumentException("Transaction element cannot be null");
        }
        if (element.getId() == null || element.getId().isBlank()) {
            throw new IllegalArgumentException("Transaction element id is required");
        }
    }

    private List<ElementDTO> safeElements(ElementDTO element) {
        return element.getElements() == null ? Collections.emptyList() : element.getElements();
    }

    private List<FlowDTO> safeFlows(ElementDTO element) {
        return element.getFlows() == null ? Collections.emptyList() : element.getFlows();
    }
    // ADD to TransactionFactory:
private FlowNode createNestedSubProcessChild(
        BpmnModelInstance modelInstance,
        SubProcess parentSubProcess,
        ElementDTO element
) {
    String type = element.getType();
    return switch (type) {
        case "userTask","serviceTask","scriptTask",
             "businessRuleTask","manualTask","sendTask","receiveTask"
                -> taskFactory.createTask(modelInstance, parentSubProcess, element);
        case "exclusiveGateway","parallelGateway","inclusiveGateway",
             "eventBasedGateway","complexGateway"
                -> gatewayFactory.createGateway(modelInstance, parentSubProcess, element);
        case "startEvent","endEvent","terminateEndEvent","errorEndEvent",
             "messageEndEvent","signalEndEvent","escalationEndEvent","cancelEndEvent",
             "intermediateCatchEvent","intermediateThrowEvent","messageEvent","timerEvent",
             "signalEvent","escalationEvent","compensationEvent","linkEvent"
                -> eventFactory.createEvent(modelInstance, parentSubProcess, element);
        case "adHocSubProcess"
                -> adHocSubProcessFactory.createAdHocSubProcess(modelInstance, parentSubProcess, element);
        default -> throw new IllegalArgumentException(
                "Unsupported nested SubProcess child type inside Transaction: " + type);
    };
}
}