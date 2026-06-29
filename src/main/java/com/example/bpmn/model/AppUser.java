package com.example.bpmn.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean active = true;

    private LocalDate joinedAt;

    protected AppUser() {
        // requis par JPA
    }

    public AppUser(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.active = true;
        this.joinedAt = LocalDate.now();
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public Role role() {
        return role;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate joinedAt() {
        return joinedAt;
    }
}