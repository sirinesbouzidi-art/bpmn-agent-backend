package com.example.bpmn.dto;

public class CamundaDeployResponse {

    private final boolean success;
    private final String deploymentKey;
    private final String message;

    public CamundaDeployResponse(boolean success, String deploymentKey, String message) {
        this.success = success;
        this.deploymentKey = deploymentKey;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getDeploymentKey() {
        return deploymentKey;
    }

    public String getMessage() {
        return message;
    }
}