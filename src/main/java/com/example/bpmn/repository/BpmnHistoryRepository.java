package com.example.bpmn.repository;

import com.example.bpmn.model.BpmnHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BpmnHistoryRepository extends JpaRepository<BpmnHistory, Long> {

    long countByGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(LocalDateTime start, LocalDateTime end);

    List<BpmnHistory> findAllByOrderByGeneratedAtDesc(Pageable pageable);
}