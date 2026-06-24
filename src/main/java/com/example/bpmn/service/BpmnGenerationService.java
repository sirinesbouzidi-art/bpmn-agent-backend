package com.example.bpmn.service;

import com.example.bpmn.converter.JsonToBpmnConverter;
import com.example.bpmn.dto.BpmnRequest;
import com.example.bpmn.dto.FlowDTO;

import org.springframework.stereotype.Service;

@Service
public class BpmnGenerationService {

    private final JsonToBpmnConverter jsonToBpmnConverter;

    public BpmnGenerationService(JsonToBpmnConverter jsonToBpmnConverter) {
        this.jsonToBpmnConverter = jsonToBpmnConverter;
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

    return jsonToBpmnConverter.convert(request);
}
}