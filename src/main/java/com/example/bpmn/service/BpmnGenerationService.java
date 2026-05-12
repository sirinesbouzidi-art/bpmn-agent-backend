package com.example.bpmn.service;

import com.example.bpmn.converter.JsonToBpmnConverter;
import com.example.bpmn.dto.BpmnRequest;
import org.springframework.stereotype.Service;

@Service
public class BpmnGenerationService {

    private final JsonToBpmnConverter jsonToBpmnConverter;

    public BpmnGenerationService(JsonToBpmnConverter jsonToBpmnConverter) {
        this.jsonToBpmnConverter = jsonToBpmnConverter;
    }

    public String generateXml(BpmnRequest request) {
        return jsonToBpmnConverter.convert(request);
    }
}