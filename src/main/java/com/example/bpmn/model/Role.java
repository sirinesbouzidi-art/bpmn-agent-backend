package com.example.bpmn.model;

import com.example.bpmn.exception.InvalidRoleException;

public enum Role {
    USER,
    ADMIN;

    public static Role fromRequest(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return USER;
        }

        try {
            return Role.valueOf(requestedRole.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRoleException("Role must be either USER or ADMIN");
        }
    }
}