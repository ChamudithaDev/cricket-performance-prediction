package com.cricketprediction.backend.service;

import com.cricketprediction.backend.config.FlaskServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlaskProcessManagerTest {

    @Mock
    private RestTemplate healthRestTemplate;

    private FlaskServiceProperties properties;
    private FlaskProcessManager manager;

    @BeforeEach
    void setUp() {
        properties = new FlaskServiceProperties();
        properties.getAutostart().setHealthUrl("http://localhost:5000/api/ml/health");
        properties.getAutostart().setPort(5000);
        properties.getAutostart().setTimeoutSeconds(2);
        properties.getAutostart().setPollIntervalMs(50);
        manager = new FlaskProcessManager(properties, healthRestTemplate);
    }

    @Test
    @DisplayName("start: Should detect already-healthy external Flask and not launch duplicate process")
    void testAlreadyRunningFlaskDetected() {
        properties.getAutostart().setEnabled(true);

        when(healthRestTemplate.getForEntity(eq("http://localhost:5000/api/ml/health"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"UP\"}", HttpStatus.OK));

        manager.start();

        assertTrue(manager.isReady());
        assertFalse(manager.isStartedByUs());
        assertTrue(manager.isRunning());

        // Stopping should leave external process alone
        manager.stop();
        assertFalse(manager.isReady());
        assertFalse(manager.isRunning());
    }

    @Test
    @DisplayName("start: Should not launch process when autostart.enabled is false")
    void testAutostartDisabled() {
        properties.getAutostart().setEnabled(false);

        when(healthRestTemplate.getForEntity(eq("http://localhost:5000/api/ml/health"), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        manager.start();

        assertFalse(manager.isReady());
        assertFalse(manager.isStartedByUs());
        assertTrue(manager.isRunning());

        manager.stop();
    }

    @Test
    @DisplayName("start: Should handle invalid project directory gracefully without throwing uncaught exception")
    void testInvalidProjectDirectoryHandled() {
        properties.getAutostart().setEnabled(true);
        properties.getAutostart().setProjectDir("non_existent_folder_xyz_123");

        when(healthRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        manager.start();

        assertFalse(manager.isReady());
        assertFalse(manager.isStartedByUs());

        manager.stop();
    }

    @Test
    @DisplayName("checkHealth: Returns true for 2xx responses and false for errors")
    void testCheckHealth() {
        when(healthRestTemplate.getForEntity(eq("http://localhost:5000/api/ml/health"), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"UP\"}", HttpStatus.OK))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertTrue(manager.checkHealth());
        assertFalse(manager.checkHealth());
    }

    @Test
    @DisplayName("Lifecycle metadata: Should have high priority phase and autostart enabled")
    void testLifecycleMetadata() {
        assertTrue(manager.getPhase() < 0);
        assertTrue(manager.isAutoStartup());
    }
}
