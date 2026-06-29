package com.example.bpmn.dto;

import java.time.LocalDate;

public class UserSummaryResponse {

    private String email;
    private String role;
    private boolean active;
    private LocalDate joinedAt;

    public UserSummaryResponse(String email, String role, boolean active, LocalDate joinedAt) {
        this.email = email;
        this.role = role;
        this.active = active;
        this.joinedAt = joinedAt;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getJoinedAt() {
        return joinedAt;
    }
}