package com.cricketprediction.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Returned by both /register and /login — contains the JWT and basic user info. */
@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
    private String role;
}
