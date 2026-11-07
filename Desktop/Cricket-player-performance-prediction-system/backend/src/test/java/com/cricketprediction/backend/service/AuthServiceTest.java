package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.AuthRequest;
import com.cricketprediction.backend.dto.AuthResponse;
import com.cricketprediction.backend.model.Role;
import com.cricketprediction.backend.model.User;
import com.cricketprediction.backend.repository.UserRepository;
import com.cricketprediction.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService using Mockito.
 * No Spring context is loaded — all dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock AuthenticationManager authenticationManager;

    @InjectMocks
    AuthService authService;

    private AuthRequest registerRequest;
    private AuthRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new AuthRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("Password123");
        registerRequest.setFullName("Test User");

        loginRequest = new AuthRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("Password123");
    }

    // ------------------------------------------------------------------
    // Registration tests
    // ------------------------------------------------------------------

    @Test
    void register_newEmail_returnsAuthResponse() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed_pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken("test@example.com")).thenReturn("mocked.jwt.token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFullName()).isEqualTo("Test User");
        assertThat(response.getRole()).isEqualTo("ROLE_USER");

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("test@example.com") &&
                u.getPassword().equals("hashed_pw") &&
                u.getRole() == Role.ROLE_USER
        ));
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgumentException() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void register_passwordIsHashed_notStoredAsPlaintext() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("$2a$10$hashedvalue");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(any())).thenReturn("token");

        authService.register(registerRequest);

        verify(userRepository).save(argThat(u -> !u.getPassword().equals("Password123")));
    }

    // ------------------------------------------------------------------
    // Login tests
    // ------------------------------------------------------------------

    @Test
    void login_validCredentials_returnsAuthResponse() {
        User storedUser = User.builder()
                .email("test@example.com")
                .password("hashed_pw")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(storedUser));
        when(jwtUtil.generateToken("test@example.com")).thenReturn("mocked.jwt.token");

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.getToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFullName()).isEqualTo("Test User");
        assertThat(response.getRole()).isEqualTo("ROLE_USER");

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("test@example.com", "Password123")
        );
    }

    @Test
    void login_wrongPassword_throwsBadCredentialsException() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtUtil, never()).generateToken(any());
    }
}
