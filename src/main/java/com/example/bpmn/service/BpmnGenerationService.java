package com.example.bpmn.service;

import com.example.bpmn.converter.JsonToBpmnConverter;
import com.example.bpmn.dto.BpmnRequest;
import com.example.bpmn.dto.FlowDTO;
import com.example.bpmn.model.GeneratedBpmnModel;
import com.example.bpmn.repository.GeneratedBpmnModelRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

@Service
public class BpmnGenerationService {

    private final JsonToBpmnConverter jsonToBpmnConverter;
    private final GeneratedBpmnModelRepository generatedBpmnModelRepository;
    public BpmnGenerationService(JsonToBpmnConverter jsonToBpmnConverter, GeneratedBpmnModelRepository generatedBpmnModelRepository) {
        this.jsonToBpmnConverter = jsonToBpmnConverter;
        this.generatedBpmnModelRepository = generatedBpmnModelRepository;
    }

    public String generateXml(BpmnRequest request) {

    System.out.println("========== REQUEST FLOWS ==========");

    if (request.getProcess() != null) {
        for (FlowDTO flow : request.getProcess().getFlows()) {

            System.out.println(
                    flow.getId()
                    + " | condition="
                    + flow.getCondition()
                    + " | default="
                    + flow.getDefaultFlow()
            );
        }
    }

    System.out.println("===================================");

    String xml = jsonToBpmnConverter.convert(request);
    generatedBpmnModelRepository.save(new GeneratedBpmnModel(resolveProcessName(request), resolveAuthor(), LocalDateTime.now(), "GENERATED"));
    return xml;
}

    private String resolveProcessName(BpmnRequest request) {
        if (request.getProcess() == null) {
            return "Untitled process";
        }
        if (request.getProcess().getName() != null && !request.getProcess().getName().isBlank()) {
            return request.getProcess().getName();
        }
        if (request.getProcess().getId() != null && !request.getProcess().getId().isBlank()) {
            return request.getProcess().getId();
        }
        return "Untitled process";
    }

    private String resolveAuthor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return "anonymous";
        }
        return authentication.getName();
    }
}
