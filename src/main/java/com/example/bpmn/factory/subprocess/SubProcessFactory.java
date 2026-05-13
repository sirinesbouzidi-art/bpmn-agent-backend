package com.example.bpmn.factory.subprocess;

import com.example.bpmn.dto.ElementDTO;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.factory.event.EventFactory;
import com.example.bpmn.factory.flow.FlowFactory;
import com.example.bpmn.factory.gateway.GatewayFactory;
import com.example.bpmn.factory.task.TaskFactory;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.SubProcess;

import java.util.HashMap;
import java.util.Map;

public class SubProcessFactory {

    private final TaskFactory taskFactory =
            new TaskFactory();

    private final GatewayFactory gatewayFactory =
            new GatewayFactory();

    private final EventFactory eventFactory =
            new EventFactory();

    private final FlowFactory flowFactory =
            new FlowFactory();

    public FlowNode createSubProcess(
            BpmnModelInstance modelInstance,
            org.camunda.bpm.model.bpmn.instance.Process process,
            ElementDTO element
    ) {

        SubProcess subProcess =
                modelInstance.newInstance(SubProcess.class);

        subProcess.setId(element.getId());
        subProcess.setName(element.getName());

        process.addChildElement(subProcess);

        Map<String, FlowNode> nodesById =
                new HashMap<>();

        for (ElementDTO child : element.getElements()) {

            FlowNode node =
                    createChildNode(
                            modelInstance,
                            subProcess,
                            child
                    );

            nodesById.put(child.getId(), node);
        }

        for (FlowDTO flow : element.getFlows()) {

            SequenceFlow sequenceFlow =
                    flowFactory.createSequenceFlow(
                            modelInstance,
                            subProcess,
                            flow,
                            nodesById
                    );

            subProcess.addChildElement(sequenceFlow);
        }

        return subProcess;
    }

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

                    -> taskFactory.createTask(
                            modelInstance,
                            subProcess,
                            element
                    );

            case "exclusiveGateway",
                 "parallelGateway",
                 "inclusiveGateway",
                 "eventBasedGateway",
                 "complexGateway"

                    -> gatewayFactory.createGateway(
                            modelInstance,
                            subProcess,
                            element
                    );

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
                 "signalEndEvent"

                    -> eventFactory.createEvent(
                            modelInstance,
                            subProcess,
                            element
                    );
            case "subProcess" ->

        createSubProcess(
                modelInstance,
                subProcess,
                element
        );        

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported subprocess element type: "
                                    + type
                    );
        };
    }
    public FlowNode createSubProcess(
        BpmnModelInstance modelInstance,
        SubProcess parentSubProcess,
        ElementDTO element
) {

    SubProcess subProcess =
            modelInstance.newInstance(SubProcess.class);

    subProcess.setId(element.getId());
    subProcess.setName(element.getName());

    parentSubProcess.addChildElement(subProcess);

    Map<String, FlowNode> nodesById =
            new HashMap<>();

    for (ElementDTO child : element.getElements()) {

        FlowNode node =
                createChildNode(
                        modelInstance,
                        subProcess,
                        child
                );

        nodesById.put(child.getId(), node);
    }

    for (FlowDTO flow : element.getFlows()) {

        flowFactory.createSequenceFlow(
                modelInstance,
                subProcess,
                flow,
                nodesById
        );
    }

    return subProcess;
}
}