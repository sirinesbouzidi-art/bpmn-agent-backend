package com.example.bpmn.dto;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;

public class BpmnRequest {

    @Valid
    private ProcessDTO process;



    @Valid
    private List<ProcessDTO> processes = new ArrayList<>();

    @Valid
    private List<FlowDTO> messageFlows = new ArrayList<>();

    @Valid
    private List<ElementDTO> elements = new ArrayList<>();

    @Valid
    private List<FlowDTO> flows = new ArrayList<>();
    
    
    public ProcessDTO getProcess() {
        return process;
    }

    public void setProcess(ProcessDTO process) {
        this.process = process;
    }

    
    public List<ProcessDTO> getProcesses() {
        return processes;
    }

    public void setProcesses(List<ProcessDTO> processes) {
        this.processes = processes;
    }

    public List<FlowDTO> getMessageFlows() {
        return messageFlows;
    }

    public void setMessageFlows(List<FlowDTO> messageFlows) {
        this.messageFlows = messageFlows;
    }

    public List<ElementDTO> getElements() {
        return elements;
    }

    public void setElements(List<ElementDTO> elements) {
        this.elements = elements;
    }

    public List<FlowDTO> getFlows() {
        return flows;
    }

    public void setFlows(List<FlowDTO> flows) {
        this.flows = flows;
    }
}