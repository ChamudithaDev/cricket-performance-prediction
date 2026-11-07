package com.cricketprediction.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Shared DTO for both /api/auth/register and /api/auth/login.
 * fullName is optional — only used during registration.
 */
@Data
public class AuthRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /** Only required for registration — ignored by the login endpoint. */
    private String fullName;
}
