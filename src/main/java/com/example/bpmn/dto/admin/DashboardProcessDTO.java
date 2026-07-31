package com.example.bpmn.dto.admin;

import java.time.LocalDateTime;

public record DashboardProcessDTO(String processName, String prompt, String author, LocalDateTime generationDate, String status) {
}
