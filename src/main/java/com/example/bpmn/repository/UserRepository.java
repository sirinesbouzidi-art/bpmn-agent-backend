package com.example.bpmn.repository;

import com.example.bpmn.model.AppUser;
import com.example.bpmn.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, String> {
    long countByRole(Role role);
}