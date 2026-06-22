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
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.TextAnnotation;
import org.camunda.bpm.model.bpmn.instance.Text;
import org.camunda.bpm.model.bpmn.instance.BaseElement;

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
        if (isArtifact(child.getType())) {
            createArtifact(modelInstance, subProcess, child);
        } else {
            FlowNode node = createChildNode(modelInstance, subProcess, child);
            nodesById.put(child.getId(), node);
        }
    }

    // Construire elementsById incluant les artifacts
Map<String, BaseElement> elementsById = new HashMap<>(nodesById);
subProcess.getFlowElements().forEach(fe -> elementsById.put(fe.getId(), fe));

for (FlowDTO flow : safeFlows(element)) {
    String flowType = flow.getType() == null ? "sequenceFlow" : flow.getType();
    switch (flowType) {
        case "sequenceFlow" -> flowFactory.createSequenceFlow(modelInstance, subProcess, flow, nodesById);
        case "association"  -> flowFactory.createAssociation(modelInstance, subProcess, flow, elementsById, org.camunda.bpm.model.bpmn.AssociationDirection.None);
        case "associationOne"  -> flowFactory.createAssociation(modelInstance, subProcess, flow, elementsById, org.camunda.bpm.model.bpmn.AssociationDirection.One);
        case "associationBoth" -> flowFactory.createAssociation(modelInstance, subProcess, flow, elementsById, org.camunda.bpm.model.bpmn.AssociationDirection.Both);
        default -> throw new IllegalArgumentException("Unsupported flow type in subprocess: " + flowType);
    }
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
        if (isArtifact(child.getType())) {
            createArtifact(modelInstance, subProcess, child);
        } else {
            FlowNode node = createChildNode(modelInstance, subProcess, child);
            nodesById.put(child.getId(), node);
        }
    }

    // Construire elementsById incluant les artifacts
Map<String, BaseElement> elementsById = new HashMap<>(nodesById);
subProcess.getFlowElements().forEach(fe -> elementsById.put(fe.getId(), fe));

for (FlowDTO flow : safeFlows(element)) {
    String flowType = flow.getType() == null ? "sequenceFlow" : flow.getType();
    switch (flowType) {
        case "sequenceFlow" -> flowFactory.createSequenceFlow(modelInstance, subProcess, flow, nodesById);
        case "association"  -> flowFactory.createAssociation(modelInstance, subProcess, flow, elementsById, org.camunda.bpm.model.bpmn.AssociationDirection.None);
        case "associationOne"  -> flowFactory.createAssociation(modelInstance, subProcess, flow, elementsById, org.camunda.bpm.model.bpmn.AssociationDirection.One);
        case "associationBoth" -> flowFactory.createAssociation(modelInstance, subProcess, flow, elementsById, org.camunda.bpm.model.bpmn.AssociationDirection.Both);
        default -> throw new IllegalArgumentException("Unsupported flow type in subprocess: " + flowType);
    }
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
private boolean isArtifact(String type) {
    return "dataObjectReference".equals(type)
        || "dataStoreReference".equals(type)
        || "textAnnotation".equals(type);
}

private void createArtifact(
        BpmnModelInstance modelInstance,
        SubProcess subProcess,
        ElementDTO element) {
    switch (element.getType()) {
        case "dataObjectReference" -> {
            DataObjectReference ref = modelInstance.newInstance(DataObjectReference.class);
            ref.setId(element.getId());
            ref.setAttributeValue("name", element.getName(), true);
            subProcess.addChildElement(ref);
        }
        case "dataStoreReference" -> {
            DataStoreReference ref = modelInstance.newInstance(DataStoreReference.class);
            ref.setId(element.getId());
            ref.setAttributeValue("name", element.getName(), true);
            subProcess.addChildElement(ref);
        }
        case "textAnnotation" -> {
            TextAnnotation annotation = modelInstance.newInstance(TextAnnotation.class);
            annotation.setId(element.getId());
            Text text = modelInstance.newInstance(Text.class);
            text.setTextContent(element.getName() == null ? "" : element.getName());
            annotation.setText(text);
            subProcess.addChildElement(annotation);
        }
    }
}
}