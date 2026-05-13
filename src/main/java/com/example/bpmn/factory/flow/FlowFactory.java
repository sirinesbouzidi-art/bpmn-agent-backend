package com.example.bpmn.factory.flow;

import com.example.bpmn.dto.FlowDTO;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.SubProcess;

import java.util.Map;
import java.util.UUID;

public class FlowFactory {

    // =====================================================
    // FLOWS FOR MAIN PROCESS
    // =====================================================

    public SequenceFlow createSequenceFlow(
            BpmnModelInstance modelInstance,
            Process process,
            FlowDTO flow,
            Map<String, FlowNode> flowNodes
    ) {

        return createSequenceFlowInternal(
                modelInstance,
                process,
                null,
                flow,
                flowNodes
        );
    }

    // =====================================================
    // FLOWS FOR SUBPROCESS
    // =====================================================

    public SequenceFlow createSequenceFlow(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            FlowDTO flow,
            Map<String, FlowNode> flowNodes
    ) {

        return createSequenceFlowInternal(
                modelInstance,
                null,
                subProcess,
                flow,
                flowNodes
        );
    }

    // =====================================================
    // COMMON FLOW CREATION
    // =====================================================

    private SequenceFlow createSequenceFlowInternal(
            BpmnModelInstance modelInstance,
            Process process,
            SubProcess subProcess,
            FlowDTO flow,
            Map<String, FlowNode> flowNodes
    ) {

        String sourceId = flow.getFrom();
        String targetId = flow.getTo();

        FlowNode source = flowNodes.get(sourceId);
        FlowNode target = flowNodes.get(targetId);

        if (source == null) {
            throw new IllegalArgumentException(
                    "Unknown source node: " + sourceId
            );
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "Unknown target node: " + targetId
            );
        }

        SequenceFlow sequenceFlow =
                modelInstance.newInstance(SequenceFlow.class);

        sequenceFlow.setId(resolveFlowId(flow));

        sequenceFlow.setSource(source);
        sequenceFlow.setTarget(target);

        if (flow.getName() != null) {
            sequenceFlow.setName(flow.getName());
        }

        String condition = flow.getCondition();

        if (condition != null && !condition.isBlank()) {

            ConditionExpression conditionExpression =
                    modelInstance.newInstance(
                            ConditionExpression.class
                    );

            conditionExpression.setTextContent(condition);

            sequenceFlow.setConditionExpression(
                    conditionExpression
            );
        }

        // =====================================================
        // ADD FLOW TO CONTAINER
        // =====================================================

        if (process != null) {
            process.addChildElement(sequenceFlow);
        }

        if (subProcess != null) {
            subProcess.addChildElement(sequenceFlow);
        }

        // =====================================================
        // CONNECT FLOW TO NODES
        // =====================================================

        source.getOutgoing().add(sequenceFlow);

        target.getIncoming().add(sequenceFlow);

        // =====================================================
        // DEFAULT FLOW SUPPORT
        // =====================================================

        if (Boolean.TRUE.equals(flow.getDefaultFlow())
                && source instanceof ExclusiveGateway gateway) {

            gateway.setDefault(sequenceFlow);
        }

        return sequenceFlow;
    }

    // =====================================================
    // FLOW ID GENERATION
    // =====================================================

    private String resolveFlowId(FlowDTO flow) {

        if (flow.getId() != null
                && !flow.getId().isBlank()) {

            return flow.getId();
        }

        return "flow_" + UUID.randomUUID();
    }
}