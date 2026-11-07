package com.cricketprediction.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Python Flask ML Service and its automatic lifecycle management.
 */
@Configuration
@ConfigurationProperties(prefix = "ml-service")
@Data
public class FlaskServiceProperties {

    /** Base URL of the Flask ML service (e.g. http://localhost:5000). */
    private String baseUrl = "http://localhost:5000";

    /** Configuration for automatic lifecycle management of the Flask process. */
    private Autostart autostart = new Autostart();

    @Data
    public static class Autostart {
        /** Whether to automatically launch Flask when Spring Boot starts. */
        private boolean enabled = true;

        /** Path to the Flask project directory (relative to Spring Boot root or absolute). */
        private String projectDir = "../ml-service";

        /** Explicit Python executable path. If empty, the manager will auto-discover the virtualenv. */
        private String pythonPath = "";

        /** Entry point script (e.g. app.py). */
        private String entryPoint = "app.py";

        /** Port on which Flask runs (default 5000). */
        private int port = 5000;

        /** Health-check endpoint URL for readiness polling. */
        private String healthUrl = "http://localhost:5000/api/ml/health";

        /** Maximum time in seconds to wait for Flask to become healthy on startup. */
        private int timeoutSeconds = 30;

        /** Polling interval in milliseconds between health checks during startup. */
        private long pollIntervalMs = 500;
    }
}
