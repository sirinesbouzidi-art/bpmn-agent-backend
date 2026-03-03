package com.example.bpmn.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Example protected endpoint.
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public Map<String, Object> secureTest(Authentication authentication) {
        return Map.of(
                "message", "JWT is valid. You can access protected resources.",
                "email", authentication.getName(),
                "authorities", authentication.getAuthorities()
        );
    }
}
