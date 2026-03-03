package com.example.bpmn.model;

/**
 * Lightweight user model used for in-memory authentication.
 */
public record AppUser(String email, String password, String role) {
}
