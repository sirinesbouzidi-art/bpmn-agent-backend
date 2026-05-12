package com.example.bpmn.dto;

import jakarta.validation.constraints.NotBlank;

public class ElementDTO {

    @NotBlank(message = "element id is required")
    private String id;

    @NotBlank(message = "element type is required")
    private String type;

    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}