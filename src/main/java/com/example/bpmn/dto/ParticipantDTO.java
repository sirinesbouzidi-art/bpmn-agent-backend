package com.example.bpmn.dto;

import jakarta.validation.constraints.NotBlank;

public class ParticipantDTO {

    @NotBlank(message = "participant id is required")
    private String id;

    private String name;

    @NotBlank(message = "participant processRef is required")
    private String processRef;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProcessRef() {
        return processRef;
    }

    public void setProcessRef(String processRef) {
        this.processRef = processRef;
    }
}