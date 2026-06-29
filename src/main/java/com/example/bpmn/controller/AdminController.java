package com.example.bpmn.controller;

import com.example.bpmn.dto.MessageResponse;
import com.example.bpmn.dto.RegisterRequest;
import com.example.bpmn.dto.ToggleStatusRequest;
import com.example.bpmn.dto.UserSummaryResponse;
import com.example.bpmn.model.AppUser;
import com.example.bpmn.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> createUser(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("User created successfully"));
    }

    @GetMapping
    public ResponseEntity<List<UserSummaryResponse>> listUsers() {
        List<UserSummaryResponse> result = authService.listUsers().stream().map(u -> new UserSummaryResponse(u.email(), u.role().name(), u.active(), u.joinedAt())).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable String email) {
        authService.deleteUser(email);
        return ResponseEntity.ok(new MessageResponse("User deleted successfully"));
    }

    @PatchMapping("/{email}/status")
    public ResponseEntity<MessageResponse> toggleStatus(
            @PathVariable String email,
            @RequestBody ToggleStatusRequest request) {
                authService.setUserActive(email, request.isActive());
                return ResponseEntity.ok(new MessageResponse(
                request.isActive() ? "User activated" : "User deactivated"));
            }
}