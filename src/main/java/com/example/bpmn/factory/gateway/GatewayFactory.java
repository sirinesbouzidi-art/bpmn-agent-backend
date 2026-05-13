package com.example.bpmn.factory.gateway;

import com.example.bpmn.dto.ElementDTO;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ComplexGateway;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SubProcess;

public class GatewayFactory {

    // =====================================================
    // GATEWAYS FOR MAIN PROCESS
    // =====================================================

    public FlowNode createGateway(
            BpmnModelInstance modelInstance,
            Process process,
            ElementDTO element
    ) {

        return createGatewayInternal(
                modelInstance,
                process,
                null,
                element
        );
    }

    // =====================================================
    // GATEWAYS FOR SUBPROCESS
    // =====================================================

    public FlowNode createGateway(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            ElementDTO element
    ) {

        return createGatewayInternal(
                modelInstance,
                null,
                subProcess,
                element
        );
    }

    // =====================================================
    // COMMON GATEWAY CREATION
    // =====================================================

    private FlowNode createGatewayInternal(
            BpmnModelInstance modelInstance,
            Process process,
            SubProcess subProcess,
            ElementDTO element
    ) {

        String type = element.getType();
        String id = element.getId();
        String name = element.getName();

        return switch (type) {

            case "exclusiveGateway" ->
                    createNode(
                            modelInstance,
                            process,
                            subProcess,
                            id,
                            name,
                            ExclusiveGateway.class
                    );

            case "parallelGateway" ->
                    createNode(
                            modelInstance,
                            process,
                            subProcess,
                            id,
                            name,
                            ParallelGateway.class
                    );

            case "inclusiveGateway" ->
                    createNode(
                            modelInstance,
                            process,
                            subProcess,
                            id,
                            name,
                            InclusiveGateway.class
                    );

            case "eventBasedGateway" ->
                    createNode(
                            modelInstance,
                            process,
                            subProcess,
                            id,
                            name,
                            EventBasedGateway.class
                    );

            case "complexGateway" ->
                    createNode(
                            modelInstance,
                            process,
                            subProcess,
                            id,
                            name,
                            ComplexGateway.class
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported gateway type: " + type
                    );
        };
    }

    // =====================================================
    // NODE CREATION
    // =====================================================

    private <T extends FlowNode> T createNode(
            BpmnModelInstance modelInstance,
            Process process,
            SubProcess subProcess,
            String id,
            String name,
            Class<T> clazz
    ) {

        T node = modelInstance.newInstance(clazz);

        node.setId(id);
        node.setName(name);

        if (process != null) {
            process.addChildElement(node);
        }

        if (subProcess != null) {
            subProcess.addChildElement(node);
        }

        return node;
    }
}