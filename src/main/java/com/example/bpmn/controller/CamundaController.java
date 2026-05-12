package com.example.bpmn.controller;

import com.example.bpmn.dto.BpmnRequest;
import com.example.bpmn.dto.CamundaDeployRequest;
import com.example.bpmn.dto.CamundaDeployResponse;
import com.example.bpmn.service.BpmnGenerationService;
import com.example.bpmn.service.CamundaService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CamundaController {

    private final CamundaService camundaService;
    private final BpmnGenerationService bpmnGenerationService;

     public CamundaController(CamundaService camundaService, BpmnGenerationService bpmnGenerationService) {
        this.camundaService = camundaService;
        this.bpmnGenerationService = bpmnGenerationService;
    }

    @PostMapping(value = "/generate-bpmn-xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> generateBpmnXml(@Valid @RequestBody BpmnRequest request) {
        return ResponseEntity.ok(bpmnGenerationService.generateXml(request));
    }

    @PostMapping("/api/camunda/deploy")
    public ResponseEntity<CamundaDeployResponse> deploy(@Valid @RequestBody CamundaDeployRequest request) {
        // Deployment is executed against the local Zeebe gateway (self-managed Camunda 8).
        CamundaDeployResponse response = camundaService.deployBpmn(request.getBpmnXml(), request.getProcessName());
        return ResponseEntity.ok(response);
    }
}