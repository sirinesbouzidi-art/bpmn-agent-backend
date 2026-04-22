package com.example.bpmn.service;

import com.example.bpmn.dto.LoginRequest;
import com.example.bpmn.dto.LoginResponse;
import com.example.bpmn.dto.RegisterRequest;
import com.example.bpmn.exception.EmailAlreadyExistsException;
import com.example.bpmn.exception.InvalidCredentialsException;
import com.example.bpmn.model.AppUser;
import com.example.bpmn.model.Role;
import com.example.bpmn.security.JwtUtils;
import com.example.bpmn.security.UserDetailsServiceImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       UserDetailsServiceImpl userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            String token = jwtUtils.generateToken(authentication);
            AppUser user = userDetailsService.findByEmail(request.getEmail());

            return new LoginResponse(token, "Bearer", user.email(), user.role().name());
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }
    
    // NOUVELLE MÉTHODE : register
    public void register(RegisterRequest request) {
        // Vérifier si l'email existe déjà
        if (userDetailsService.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }
        
        // Créer le nouvel utilisateur (toujours avec le rôle USER par défaut)
        Role role = Role.fromRequest(request.getRole());
        AppUser newUser = new AppUser(
            request.getEmail(),
             request.getPassword(),
            role // Note: on stocke en clair car vous utilisez {noop}
        );
        
        // Ajouter l'utilisateur à la map
        userDetailsService.registerUser(newUser);
    }
}