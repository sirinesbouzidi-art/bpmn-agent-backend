package com.example.bpmn.dto.admin;

public record DashboardSystemStatusDTO(
        DashboardStatusItemDTO backend,
        DashboardStatusItemDTO fastAPI,
        DashboardStatusItemDTO database,
        DashboardStatusItemDTO camunda
) {
}
