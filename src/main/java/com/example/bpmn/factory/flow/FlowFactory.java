package com.example.bpmn.factory.flow;
 
import com.example.bpmn.dto.FlowDTO;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.Transaction; 
 
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
 
public class FlowFactory {
    private static final Pattern NON_ID_CHARACTER = Pattern.compile("[^A-Za-z0-9_.-]");
 
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