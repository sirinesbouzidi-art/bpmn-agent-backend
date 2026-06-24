package com.example.bpmn.factory.flow;
 
import com.example.bpmn.dto.FlowDTO;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.Transaction; 
import org.camunda.bpm.model.bpmn.instance.Association;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.AssociationDirection;
import org.camunda.bpm.model.bpmn.instance.InteractionNode;
import org.camunda.bpm.model.bpmn.instance.Activity;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
 
public class FlowFactory {
    private static final Pattern NON_ID_CHARACTER = Pattern.compile("[^A-Za-z0-9_.-]");
    public MessageFlow createMessageFlow(BpmnModelInstance modelInstance, BaseElement container, FlowDTO flow, Map<String, BaseElement> elementsById) {
        MessageFlow messageFlow = modelInstance.newInstance(MessageFlow.class);
        messageFlow.setId(resolveFlowId(modelInstance, flow, flow.getFrom(), flow.getTo()));
        if (flow.getName() != null) messageFlow.setName(flow.getName());
        BaseElement source = requireElement(elementsById, flow.getFrom(), "messageFlow source");
        BaseElement target = requireElement(elementsById, flow.getTo(), "messageFlow target");
        if (!(source instanceof InteractionNode src)) throw new IllegalArgumentException("messageFlow source must be InteractionNode: " + flow.getFrom());
        if (!(target instanceof InteractionNode tgt)) throw new IllegalArgumentException("messageFlow target must be InteractionNode: " + flow.getTo());
        messageFlow.setSource(src);
        messageFlow.setTarget(tgt);
        container.addChildElement(messageFlow);
        return messageFlow;
    }

        public DataInputAssociation createDataInputAssociation(BpmnModelInstance modelInstance, FlowDTO flow, Map<String, BaseElement> elementsById) {
        BaseElement source = requireElement(elementsById, flow.getFrom(), "dataInputAssociation source");
        BaseElement target = requireElement(elementsById, flow.getTo(), "dataInputAssociation target");
        if (!(source instanceof ItemAwareElement sourceItem)) throw new IllegalArgumentException("dataInputAssociation source must be ItemAwareElement: " + flow.getFrom());
        if (!(target instanceof Activity activityTarget)) throw new IllegalArgumentException("dataInputAssociation target must be Activity: " + flow.getTo());

        DataInputAssociation association = modelInstance.newInstance(DataInputAssociation.class);
        association.setId(resolveFlowId(modelInstance, flow, flow.getFrom(), flow.getTo()));
        association.getSources().add(sourceItem);
        activityTarget.getDataInputAssociations().add(association);
        return association;
    }

    public DataOutputAssociation createDataOutputAssociation(BpmnModelInstance modelInstance, FlowDTO flow, Map<String, BaseElement> elementsById) {
        BaseElement source = requireElement(elementsById, flow.getFrom(), "dataOutputAssociation source");
        BaseElement target = requireElement(elementsById, flow.getTo(), "dataOutputAssociation target");
        if (!(source instanceof Activity activitySource)) throw new IllegalArgumentException("dataOutputAssociation source must be Activity: " + flow.getFrom());
        if (!(target instanceof ItemAwareElement targetItem)) throw new IllegalArgumentException("dataOutputAssociation target must be ItemAwareElement: " + flow.getTo());

        DataOutputAssociation association = modelInstance.newInstance(DataOutputAssociation.class);
        association.setId(resolveFlowId(modelInstance, flow, flow.getFrom(), flow.getTo()));
        association.setTarget(targetItem);
        activitySource.getDataOutputAssociations().add(association);
        return association;
    }

    public Association createAssociation(BpmnModelInstance modelInstance, BaseElement container, FlowDTO flow, Map<String, BaseElement> elementsById, AssociationDirection direction) {
        Association association = modelInstance.newInstance(Association.class);
        association.setId(resolveFlowId(modelInstance, flow, flow.getFrom(), flow.getTo()));
        BaseElement source = requireElement(elementsById, flow.getFrom(), "association source");
        BaseElement target = requireElement(elementsById, flow.getTo(), "association target");
        association.setSource(source);
        association.setTarget(target);
        association.setAssociationDirection(direction == null ? AssociationDirection.None : direction);
        container.addChildElement(association);
        return association;
    }
 
    // ── Existing overload — Process ──────────────────────────────────
    public SequenceFlow createSequenceFlow(
            BpmnModelInstance modelInstance,
            Process process,
            FlowDTO flow,
            Map<String, FlowNode> flowNodes
    ) {
        return createSequenceFlowInternal(modelInstance, process, null, null, flow, flowNodes);
    }
 
    // ── Existing overload — SubProcess ───────────────────────────────
    public SequenceFlow createSequenceFlow(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            FlowDTO flow,
            Map<String, FlowNode> flowNodes
    ) {
        return createSequenceFlowInternal(modelInstance, null, subProcess, null, flow, flowNodes);
    }
 
    // ── NEW overload — Transaction ────────────────────────────────────
    public SequenceFlow createSequenceFlow(
            BpmnModelInstance modelInstance,
            Transaction transaction,
            FlowDTO flow,
            Map<String, FlowNode> flowNodes
    ) {
        return createSequenceFlowInternal(modelInstance, null, null, transaction, flow, flowNodes);
    }
 
    // ── Core implementation — handles all three parent types ──────────
    private SequenceFlow createSequenceFlowInternal(
            BpmnModelInstance modelInstance,
            Process process,
            SubProcess subProcess,
            Transaction transaction,         // ← NEW parameter
            FlowDTO flow,
            Map<String, FlowNode> flowNodes
    ) {
        if (flow == null) {
            throw new IllegalArgumentException("BPMN sequence flow cannot be null");
        }
 
        String sourceId = flow.getFrom();
        String targetId = flow.getTo();
        FlowNode source = flowNodes.get(sourceId);
        FlowNode target = flowNodes.get(targetId);
 
        if (source == null) {
            throw new IllegalArgumentException("Unknown source node: " + sourceId);
        }
        if (target == null) {
            throw new IllegalArgumentException("Unknown target node: " + targetId);
        }
 
        SequenceFlow sequenceFlow = modelInstance.newInstance(SequenceFlow.class);
        sequenceFlow.setId(resolveFlowId(modelInstance, flow, sourceId, targetId));
        sequenceFlow.setSource(source);
        sequenceFlow.setTarget(target);
 
        if (flow.getName() != null) {
            sequenceFlow.setName(flow.getName());
        }
        System.out.println(
             "FLOW="
              + flow.getId()
              + " | condition="
              + flow.getCondition()
              + " | default="
              + flow.getDefaultFlow()
            );
 
        String condition = flow.getCondition();
        if (condition != null && !condition.isBlank()) {
            ConditionExpression conditionExpression = modelInstance.newInstance(ConditionExpression.class);
            conditionExpression.setTextContent(condition);
            sequenceFlow.setConditionExpression(conditionExpression);
        }
 
        // Add to the correct parent container
        if (process != null) {
            process.addChildElement(sequenceFlow);
        }
        if (subProcess != null) {
            subProcess.addChildElement(sequenceFlow);
        }
        if (transaction != null) {
            transaction.addChildElement(sequenceFlow);  // ← NEW
        }
 
        source.getOutgoing().add(sequenceFlow);
        target.getIncoming().add(sequenceFlow);
 
        if (Boolean.TRUE.equals(flow.getDefaultFlow()) && source instanceof ExclusiveGateway gateway) {
            gateway.setDefault(sequenceFlow);
        }
 
        return sequenceFlow;
    }
 
    private String resolveFlowId(
            BpmnModelInstance modelInstance,
            FlowDTO flow,
            String sourceId,
            String targetId
    ) {
        if (flow.getId() != null && !flow.getId().isBlank()) {
            return uniqueId(modelInstance, flow.getId());
        }
        String fingerprint = UUID.nameUUIDFromBytes((sourceId
                + "|" + targetId
                + "|" + String.valueOf(flow.getName())
                + "|" + String.valueOf(flow.getCondition())
                + "|" + String.valueOf(flow.getDefaultFlow())).getBytes(StandardCharsets.UTF_8))
                .toString()
                .substring(0, 8);
        return uniqueId(modelInstance, "flow_" + sourceId + "_" + targetId + "_" + fingerprint);
    }
    private BaseElement requireElement(Map<String, BaseElement> elementsById, String id, String role) {
        BaseElement element = elementsById.get(id);
        if (element == null) throw new IllegalArgumentException("Unknown " + role + ": " + id);
        return element;
    }
 
    private String uniqueId(BpmnModelInstance modelInstance, String requestedId) {
        String normalizedId = NON_ID_CHARACTER.matcher(requestedId.trim()).replaceAll("_");
        if (normalizedId.isBlank()) {
            normalizedId = "flow";
        }
        if (!normalizedId.matches("[A-Za-z_].*")) {
            normalizedId = "flow_" + normalizedId;
        }
        String candidate = normalizedId;
        int suffix = 2;
        while (modelInstance.getModelElementById(candidate) != null) {
            candidate = normalizedId + "_" + suffix;
            suffix++;
        }
        return candidate;
    }
}