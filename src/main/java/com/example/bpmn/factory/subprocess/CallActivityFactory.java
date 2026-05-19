package com.example.bpmn.factory.subprocess;

import com.example.bpmn.dto.ElementDTO;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.CallActivity;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaIn;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaOut;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating BPMN CallActivity elements.
 *
 * Follows the exact same pattern as TaskFactory, GatewayFactory, EventFactory.
 * DO NOT add layout logic here — layout is handled by JsonToBpmnConverter.
 *
 * Supported ElementDTO fields:
 *   - id           (required) : element ID
 *   - name         (optional) : display label
 *   - calledElement (required) : ID of the called process definition
 *   - variables    (optional) : "all" → maps all variables in/out automatically
 *   - in           (optional) : list of CamundaIn variable mappings
 *   - out          (optional) : list of CamundaOut variable mappings
 *
 * JSON example:
 * {
 *   "id": "call_subprocess",
 *   "type": "callActivity",
 *   "name": "Run Sub-Process",
 *   "calledElement": "mySubProcessDefinitionKey",
 *   "variables": "all"
 * }
 *
 * JSON example with explicit in/out mappings:
 * {
 *   "id": "call_subprocess",
 *   "type": "callActivity",
 *   "name": "Run Sub-Process",
 *   "calledElement": "mySubProcessDefinitionKey",
 *   "in": [
 *     { "source": "localVar", "target": "calledVar" }
 *   ],
 *   "out": [
 *     { "source": "result", "target": "parentResult" }
 *   ]
 * }
 */
public class CallActivityFactory {

    // =====================================================
    // CALL ACTIVITY FOR MAIN PROCESS
    // =====================================================

    public FlowNode createCallActivity(
            BpmnModelInstance modelInstance,
            Process process,
            ElementDTO element
    ) {
        CallActivity callActivity = buildCallActivity(modelInstance, element);
        process.addChildElement(callActivity);
        return callActivity;
    }

    // =====================================================
    // CALL ACTIVITY FOR SUBPROCESS (nested)
    // =====================================================

    public FlowNode createCallActivity(
            BpmnModelInstance modelInstance,
            SubProcess subProcess,
            ElementDTO element
    ) {
        CallActivity callActivity = buildCallActivity(modelInstance, element);
        subProcess.addChildElement(callActivity);
        return callActivity;
    }

    // =====================================================
    // CORE CONSTRUCTION — shared by both overloads
    // =====================================================

    private CallActivity buildCallActivity(
            BpmnModelInstance modelInstance,
            ElementDTO element
    ) {
        if (element == null) {
            throw new IllegalArgumentException("CallActivity element cannot be null");
        }

        String id = element.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CallActivity element id is required");
        }

        String calledElement = element.getCalledElement();
        if (calledElement == null || calledElement.isBlank()) {
            throw new IllegalArgumentException(
                    "CallActivity '" + id + "' requires a 'calledElement' (called process definition key)"
            );
        }

        CallActivity callActivity = modelInstance.newInstance(CallActivity.class);
        callActivity.setId(id);
        callActivity.setName(element.getName());
        callActivity.setCalledElement(calledElement);

        // ── Variable mappings ──────────────────────────────────────────────────
        // Option A: "variables": "all"  →  single CamundaIn/CamundaOut with variables="all"
        // Option B: explicit "in" / "out" lists
        // Both options can coexist (A sets the global transfer, B adds fine-grained mappings)

        String variables = element.getVariables();
        if ("all".equalsIgnoreCase(variables)) {
            addCamundaIn(modelInstance, callActivity, null, null, "all");
            addCamundaOut(modelInstance, callActivity, null, null, "all");
        }

        for (Map<String, String> inMapping : safeList(element.getIn())) {
            String source = inMapping.get("source");
            String target = inMapping.get("target");
            if (source != null && !source.isBlank()) {
                addCamundaIn(modelInstance, callActivity, source, target, null);
            }
        }

        for (Map<String, String> outMapping : safeList(element.getOut())) {
            String source = outMapping.get("source");
            String target = outMapping.get("target");
            if (source != null && !source.isBlank()) {
                addCamundaOut(modelInstance, callActivity, source, target, null);
            }
        }

        return callActivity;
    }

    // =====================================================
    // CAMUNDA EXTENSION — CamundaIn
    // =====================================================

    /**
     * Adds a camunda:in extension element.
     *
     * @param source    source variable name (null when variables="all")
     * @param target    target variable name in the called process (null when variables="all")
     * @param variables pass "all" to transfer all variables; null for explicit mapping
     */
    private void addCamundaIn(
            BpmnModelInstance modelInstance,
            CallActivity callActivity,
            String source,
            String target,
            String variables
    ) {
        CamundaIn camundaIn = modelInstance.newInstance(CamundaIn.class);

        if ("all".equals(variables)) {
            camundaIn.setCamundaVariables("all");
        } else {
            camundaIn.setCamundaSource(source);
            camundaIn.setCamundaTarget(target != null ? target : source);
        }

        callActivity.builder()
                .addExtensionElement(camundaIn);
    }

    // =====================================================
    // CAMUNDA EXTENSION — CamundaOut
    // =====================================================

    /**
     * Adds a camunda:out extension element.
     *
     * @param source    source variable name in the called process (null when variables="all")
     * @param target    target variable name in the parent process (null when variables="all")
     * @param variables pass "all" to transfer all variables; null for explicit mapping
     */
    private void addCamundaOut(
            BpmnModelInstance modelInstance,
            CallActivity callActivity,
            String source,
            String target,
            String variables
    ) {
        CamundaOut camundaOut = modelInstance.newInstance(CamundaOut.class);

        if ("all".equals(variables)) {
            camundaOut.setCamundaVariables("all");
        } else {
            camundaOut.setCamundaSource(source);
            camundaOut.setCamundaTarget(target != null ? target : source);
        }

        callActivity.builder()
                .addExtensionElement(camundaOut);
    }

    // =====================================================
    // UTILITY
    // =====================================================

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}