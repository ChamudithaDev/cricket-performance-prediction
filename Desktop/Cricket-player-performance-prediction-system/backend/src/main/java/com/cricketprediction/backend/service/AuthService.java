package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.AuthRequest;
import com.cricketprediction.backend.dto.AuthResponse;
import com.cricketprediction.backend.model.Role;
import com.cricketprediction.backend.model.User;
import com.cricketprediction.backend.repository.UserRepository;
import com.cricketprediction.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new user with ROLE_USER.
     * Throws IllegalArgumentException if the email is already taken.
     */
    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName() != null ? request.getFullName().trim() : "")
                .role(Role.ROLE_USER)
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name());
    }

    /**
     * Authenticate an existing user and return a JWT.
     * AuthenticationManager throws BadCredentialsException on wrong password —
     * handled by GlobalExceptionHandler.
     */
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
