package com.example.bpmn.dto;

import jakarta.validation.constraints.NotBlank;

public class FlowDTO {

    private String id;

    @NotBlank(message = "flow from is required")
    private String from;

    @NotBlank(message = "flow to is required")
    private String to;

    private String name;
    private String condition;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}