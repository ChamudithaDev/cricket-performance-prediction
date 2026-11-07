package com.cricketprediction.backend.security;

import com.cricketprediction.backend.model.Role;
import com.cricketprediction.backend.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthSecurityTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthFilter = new JwtAuthFilter(jwtUtil, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate user and set ROLE_USER authority with valid JWT Bearer token")
    void testValidJwtTokenAuthenticatesUser() throws Exception {
        String token = "valid.jwt.token";
        String email = "testuser@cricketai.com";
        User user = User.builder()
                .email(email)
                .password("hash")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractEmail(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(user);
        when(jwtUtil.isTokenValid(token, email)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo(email);
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should leave SecurityContext unauthenticated when no Authorization header is provided")
    void testNoAuthorizationHeaderLeavesUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtUtil, userDetailsService);
    }

    @Test
    @DisplayName("Should leave SecurityContext unauthenticated when token is malformed or invalid")
    void testMalformedJwtTokenLeavesUnauthenticated() throws Exception {
        String token = "invalid.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractEmail(token)).thenThrow(new RuntimeException("Malformed token"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Should handle UsernameNotFoundException gracefully without throwing unhandled error")
    void testUserNotFoundLeavesUnauthenticated() throws Exception {
        String token = "valid.jwt.token";
        String email = "deleted@cricketai.com";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractEmail(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email))
                .thenThrow(new UsernameNotFoundException("User deleted"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
