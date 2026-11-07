package com.cricketprediction.backend.exception;

/**
 * Thrown when an error occurs while communicating with an external third-party API (e.g. Cricket Data API).
 */
public class ExternalApiException extends RuntimeException {

    public ExternalApiException(String message) {
        super(message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
