package com.cricketprediction.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource (e.g. a player) does not exist.
 * Spring MVC will map this to HTTP 404 Not Found via @ResponseStatus.
 * GlobalExceptionHandler has a catch-all, but keeping @ResponseStatus
 * here means the correct status is returned even if the handler doesn't
 * explicitly handle this type.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
