package com.example.bpmn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public class ElementDTO {

    @NotBlank(message = "element id is required")
    private String id;

    @NotBlank(message = "element type is required")
    private String type;

    private String name;

    @Valid
    private List<ElementDTO> elements = new ArrayList<>();

    @Valid
    private List<FlowDTO> flows = new ArrayList<>();

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