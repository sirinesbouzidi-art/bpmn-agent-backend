package com.example.bpmn.dto;

import java.util.List;

public class LaneDTO {

    private String id;

    private String name;

    private List<String> elementRefs;

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

    public List<String> getElementRefs() {
        return elementRefs;
    }

    public void setElementRefs(List<String> elementRefs) {
        this.elementRefs = elementRefs;
    }
}
