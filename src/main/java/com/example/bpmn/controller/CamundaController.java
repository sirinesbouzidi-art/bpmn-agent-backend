package com.example.bpmn.controller;

import com.example.bpmn.dto.CamundaDeployRequest;
import com.example.bpmn.dto.CamundaDeployResponse;
import com.example.bpmn.service.CamundaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/camunda")
public class CamundaController {

    private final CamundaService camundaService;

    public CamundaController(CamundaService camundaService) {
        this.camundaService = camundaService;
    }

    @PostMapping("/deploy")
    public ResponseEntity<CamundaDeployResponse> deploy(@Valid @RequestBody CamundaDeployRequest request) {
        // Deployment is executed against the local Zeebe gateway (self-managed Camunda 8).
        CamundaDeployResponse response = camundaService.deployBpmn(request.getBpmnXml(), request.getProcessName());
        return ResponseEntity.ok(response);
    }
}