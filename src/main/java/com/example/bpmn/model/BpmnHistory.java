package com.example.bpmn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "bpmn_history")
public class BpmnHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String processName;

    @Column(length = 4000)
    private String prompt;

    private LocalDateTime generatedAt;

    private String author;

    private String status;

    protected BpmnHistory() {
        // Required by JPA.
    }

    public Long getId() {
        return id;
    }

    public String getProcessName() {
        return processName;
    }

    public String getPrompt() {
        return prompt;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public String getAuthor() {
        return author;
    }

    public String getStatus() {
        return status;
    }
}