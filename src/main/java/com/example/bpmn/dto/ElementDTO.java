package com.example.bpmn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ElementDTO {

    @NotBlank(message = "element id is required")
    private String id;

    @NotBlank(message = "element type is required")
    private String type;
    private String name;

    private String calledElement;
    private String variables;

    private String triggerType;
    private Boolean interrupting;

    private String ordering;
    private Boolean cancelRemainingInstances;
    private String completionCondition;
    private Boolean adHoc;
    private String parentId;

    private List<Map<String, String>> in;

    private List<Map<String, String>> out;
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
    public String getOrdering() {
        return ordering;
    }

    public void setOrdering(String ordering) {
        this.ordering = ordering;
    }

    public Boolean getCancelRemainingInstances() {
        return cancelRemainingInstances;
    }

    public void setCancelRemainingInstances(
        Boolean cancelRemainingInstances
        ) {
        this.cancelRemainingInstances =
            cancelRemainingInstances;
    }

    public String getCompletionCondition() {
        return completionCondition;
    }

    public void setCompletionCondition(
        String completionCondition
   ) {
        this.completionCondition =
            completionCondition;
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
    public String getCalledElement() {
        return calledElement;
    }

    public void setCalledElement(String calledElement) {
        this.calledElement = calledElement;
    }

    public String getVariables() {
        return variables;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }

    public List<Map<String, String>> getIn() {
        return in;
    }

    public void setIn(List<Map<String, String>> in) {
        this.in = in;
    }

    public List<Map<String, String>> getOut() {
        return out;
    }

    public void setOut(List<Map<String, String>> out) {
        this.out = out;
    }
    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public Boolean getInterrupting() {
        return interrupting;
    }

    public void setInterrupting(Boolean interrupting) {
        this.interrupting = interrupting;
    }

    public Boolean getAdHoc() {
       return adHoc;
    }

    public void setAdHoc(Boolean adHoc) {
       this.adHoc = adHoc;
    }
   

    public String getParentId() {
    return parentId;
    }

   public void setParentId(String parentId) {
    this.parentId = parentId;
}

}