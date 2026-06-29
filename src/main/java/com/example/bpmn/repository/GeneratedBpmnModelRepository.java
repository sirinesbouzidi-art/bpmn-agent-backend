package com.example.bpmn.repository;

import com.example.bpmn.model.GeneratedBpmnModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GeneratedBpmnModelRepository extends JpaRepository<GeneratedBpmnModel, Long> {

    long countByGenerationDateBetween(LocalDateTime start, LocalDateTime end);

    List<GeneratedBpmnModel> findTop5ByOrderByGenerationDateDesc();

    List<GeneratedBpmnModel> findTop10ByOrderByGenerationDateDesc();
}