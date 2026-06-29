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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager, JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate( new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()) );
            String token = jwtUtils.generateToken(authentication);
            AppUser user = userDetailsService.findByEmail(request.getEmail());

            return new LoginResponse(token, "Bearer", user.email(), user.role().name());
        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        } catch (org.springframework.security.authentication.InternalAuthenticationServiceException ex) {
        // Spring Security enveloppe parfois UsernameNotFoundException dans cette exception
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }

    /**
     * Création d'un compte par l'admin (teamlead). N'est plus exposée publiquement —
     * appelée uniquement depuis AdminController, protégé par ROLE_ADMIN.
     */
    public void register(RegisterRequest request) {
        if (userDetailsService.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }

        Role role = Role.fromRequest(request.getRole());
        AppUser newUser = new AppUser(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                role
        );

        userDetailsService.registerUser(newUser);
    }

    public void deleteUser(String email) {
        if (!userDetailsService.existsByEmail(email)) {
            throw new com.example.bpmn.exception.InvalidCredentialsException("User not found: " + email);
        }
        userDetailsService.deleteUser(email);
    }
    public void setUserActive(String email, boolean active) {
    AppUser user = userDetailsService.findByEmail(email);
    if (user == null) {
        throw new InvalidCredentialsException("User not found: " + email);
    }
    if (user.role() == Role.ADMIN) {
        throw new InvalidCredentialsException("Admin accounts cannot be deactivated");
    }
    user.setActive(active);
    userDetailsService.registerUser(user);
    }

    public List<AppUser> listUsers() {
        return userDetailsService.findAll();
    }
}