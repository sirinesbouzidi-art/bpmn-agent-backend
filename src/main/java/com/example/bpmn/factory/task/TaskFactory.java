package com.example.bpmn.factory.task;

import com.example.bpmn.dto.ElementDTO;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BusinessRuleTask;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.ManualTask;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.ScriptTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.UserTask;


public class TaskFactory {
 // TASKS FOR MAIN PROCESS
    public FlowNode createTask(
            BpmnModelInstance modelInstance,
            Process process,
            ElementDTO element
    ) {

        String type = element.getType();
        String id = element.getId();
        String name = element.getName();

        return switch (type) {

            case "userTask" ->
                    createNode(
                            modelInstance,
                            process,
                            id,
                            name,
                            UserTask.class
                    );

            case "serviceTask" ->
                    createNode(
                            modelInstance,
                            process,
                            id,
                            name,
                            ServiceTask.class
                    );

            case "scriptTask" ->
                    createNode(
                            modelInstance,
                            process,
                            id,
                            name,
                            ScriptTask.class
                    );

            case "businessRuleTask" ->
                    createNode(
                            modelInstance,
                            process,
                            id,
                            name,
                            BusinessRuleTask.class
                    );

            case "manualTask" ->
                    createNode(
                            modelInstance,
                            process,
                            id,
                            name,
                            ManualTask.class
                    );

            case "sendTask" ->
                    createNode(
                            modelInstance,
                            process,
                            id,
                            name,
                            SendTask.class
                    );

            case "receiveTask" ->
                    createNode(
                            modelInstance,
                            process,
                            id,
                            name,
                            ReceiveTask.class
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported task type: " + type
                    );
        };
    }

 // TASKS FOR SUBPROCESS
    public FlowNode createTask(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            ElementDTO element
    ) {

        String type = element.getType();
        String id = element.getId();
        String name = element.getName();

        return switch (type) {

            case "userTask" ->
                    createNode(
                            modelInstance,
                            subProcess,
                            id,
                            name,
                            UserTask.class
                    );

            case "serviceTask" ->
                    createNode(
                            modelInstance,
                            subProcess,
                            id,
                            name,
                            ServiceTask.class
                    );

            case "scriptTask" ->
                    createNode(
                            modelInstance,
                            subProcess,
                            id,
                            name,
                            ScriptTask.class
                    );

            case "businessRuleTask" ->
                    createNode(
                            modelInstance,
                            subProcess,
                            id,
                            name,
                            BusinessRuleTask.class
                    );

            case "manualTask" ->
                    createNode(
                            modelInstance,
                            subProcess,
                            id,
                            name,
                            ManualTask.class
                    );

            case "sendTask" ->
                    createNode(
                            modelInstance,
                            subProcess,
                            id,
                            name,
                            SendTask.class
                    );

            case "receiveTask" ->
                    createNode(
                            modelInstance,
                            subProcess,
                            id,
                            name,
                            ReceiveTask.class
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported task type: " + type
                    );
        };
    }

// NODE CREATION FOR PROCESS
    private <T extends FlowNode> T createNode(
            BpmnModelInstance modelInstance,
            Process process,
            String id,
            String name,
            Class<T> clazz
    ) {

        T node = modelInstance.newInstance(clazz);

        node.setId(id);
        node.setName(name);

        process.addChildElement(node);

        return node;
    }
// NODE CREATION FOR SUBPROCESS
    private <T extends FlowNode> T createNode(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            String id,
            String name,
            Class<T> clazz
    ) {

        T node = modelInstance.newInstance(clazz);

        node.setId(id);
        node.setName(name);

        subProcess.addChildElement(node);

        return node;
    }
}