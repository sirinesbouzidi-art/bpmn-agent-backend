package com.example.bpmn.security;

import com.example.bpmn.model.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * In-memory user store; can later be replaced by JPA repository.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final Map<String, AppUser> users = Map.of(
            "admin@bouygues.com", new AppUser("admin@bouygues.com", "admin123", "ADMIN"),
            "user@bouygues.com", new AppUser("user@bouygues.com", "user123", "USER")
    );

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser appUser = users.get(email);
        if (appUser == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + appUser.role());
        return new User(appUser.email(), "{noop}" + appUser.password(), List.of(authority));
    }

    public AppUser findByEmail(String email) {
        return users.get(email);
    }
}
