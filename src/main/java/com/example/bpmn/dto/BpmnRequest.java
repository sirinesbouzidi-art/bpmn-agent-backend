package com.example.bpmn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BpmnRequest {

    @Valid
    @NotNull(message = "process is required")
    private ProcessDTO process;

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