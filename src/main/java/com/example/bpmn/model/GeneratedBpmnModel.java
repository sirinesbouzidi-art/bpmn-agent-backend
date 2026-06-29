package com.example.bpmn.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_bpmn_models")
public class GeneratedBpmnModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String processName;
    private String author;
    private LocalDateTime generationDate;
    private String status;

    protected GeneratedBpmnModel() {
    }

    public GeneratedBpmnModel(String processName, String author, LocalDateTime generationDate, String status) {
        this.processName = processName;
        this.author = author;
        this.generationDate = generationDate;
        this.status = status;
    }

    public Long id() { return id; }
    public String processName() { return processName; }
    public String author() { return author; }
    public LocalDateTime generationDate() { return generationDate; }
    public String status() { return status; }
}