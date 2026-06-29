package com.example.bpmn.dto.admin;

import java.time.LocalDateTime;

public record DashboardActivityDTO(String label, String value, LocalDateTime date, String icon) {
}