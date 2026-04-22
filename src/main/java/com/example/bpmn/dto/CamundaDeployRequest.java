package com.example.bpmn.dto;

import jakarta.validation.constraints.NotBlank;

public class CamundaDeployRequest {

    @NotBlank(message = "bpmnXml is required")
    private String bpmnXml;

    @NotBlank(message = "processName is required")
    private String processName;

    public String getBpmnXml() {
        return bpmnXml;
    }

    public void setBpmnXml(String bpmnXml) {
        this.bpmnXml = bpmnXml;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }
}