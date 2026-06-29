package com.example.bpmn.security;

import com.example.bpmn.model.AppUser;
import com.example.bpmn.model.Role;
import com.example.bpmn.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDetailsServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crée le compte admin par défaut une seule fois, au premier démarrage.
     * Si la base existe déjà (redémarrages suivants), ne touche à rien.
     */
    @PostConstruct
    public void seedDefaultAdmin() {
        if (!userRepository.existsById("admin@bouygues.com")) {
            AppUser admin = new AppUser(
                    "admin@bouygues.com",
                    passwordEncoder.encode("admin123"),
                    Role.ADMIN
            );
            userRepository.save(admin);
        }
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser appUser = userRepository.findById(email).orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        if (!appUser.active()) {
            throw new UsernameNotFoundException("Account is deactivated: " + email);
        }
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + appUser.role().name());
            return new User(appUser.email(), appUser.password(), List.of(authority));
    }

    public AppUser findByEmail(String email) {
        return userRepository.findById(email).orElse(null);
    }

    public void registerUser(AppUser newUser) {
        userRepository.save(newUser);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsById(email);
    }

    public void deleteUser(String email) {
        userRepository.deleteById(email);
    }

    public List<AppUser> findAll() {
        return userRepository.findAll();
    }
}