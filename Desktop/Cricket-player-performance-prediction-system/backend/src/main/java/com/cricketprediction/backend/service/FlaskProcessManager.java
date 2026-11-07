package com.cricketprediction.backend.service;

import com.cricketprediction.backend.config.FlaskServiceProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the automatic OS-level process lifecycle of the Python Flask ML Service.
 *
 * Responsibilities:
 *  - Discovers and validates project directory, virtualenv Python executable, and entry point.
 *  - Launches Flask via ProcessBuilder with proper working directory and unbuffered output.
 *  - Detects if Flask is already running to prevent duplicate process creation.
 *  - Captures and logs Flask stdout/stderr asynchronously.
 *  - Polls the Flask health-check endpoint (/api/ml/health) until healthy or timeout.
 *  - Gracefully terminates Flask and any child processes when Spring Boot shuts down,
 *    without terminating externally managed Flask instances.
 */
@Service
@Slf4j
public class FlaskProcessManager implements SmartLifecycle {

    private final FlaskServiceProperties properties;
    private final RestTemplate healthRestTemplate;

    private Process flaskProcess;

    @Getter
    private boolean startedByUs = false;

    private volatile boolean ready = false;
    private volatile boolean running = false;

    @org.springframework.beans.factory.annotation.Autowired
    public FlaskProcessManager(FlaskServiceProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.healthRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(800))
                .setReadTimeout(Duration.ofMillis(1200))
                .build();
    }

    // Constructor for testing with custom RestTemplate
    public FlaskProcessManager(FlaskServiceProperties properties, RestTemplate healthRestTemplate) {
        this.properties = properties;
        this.healthRestTemplate = healthRestTemplate;
    }

    @Override
    public int getPhase() {
        // High priority: start before web controllers handle traffic, stop late on shutdown
        return Integer.MIN_VALUE + 500;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    /**
     * Returns true only when Flask has been verified healthy and ready to accept prediction requests.
     */
    public synchronized boolean isReady() {
        if (ready && startedByUs && flaskProcess != null && !flaskProcess.isAlive()) {
            log.warn("[ML Autostart] Flask process terminated unexpectedly (exit code: {}). Service is no longer ready.",
                    flaskProcess.exitValue());
            ready = false;
        }
        return ready;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        FlaskServiceProperties.Autostart config = properties.getAutostart();
        if (!config.isEnabled()) {
            log.info("[ML Autostart] Automatic Flask startup is disabled via configuration (ml-service.autostart.enabled=false).");
            ready = checkHealth();
            if (ready) {
                log.info("[ML Autostart] External Flask service detected healthy at {}.", config.getHealthUrl());
            }
            return;
        }

        // 1. Check if Flask is ALREADY running and healthy
        if (checkHealth()) {
            startedByUs = false;
            ready = true;
            log.info("[ML Autostart] Flask ML service is ALREADY running and healthy at {} (port {}). Reusing existing instance.",
                    config.getHealthUrl(), config.getPort());
            return;
        }

        // 2. Check if port is already bound by another process
        if (isPortBound(config.getPort())) {
            startedByUs = false;
            ready = false;
            log.error("[ML Autostart] Port {} is already in use by another process, but the ML health check did not pass at {}. " +
                    "Aborting automatic launch to prevent port collision.", config.getPort(), config.getHealthUrl());
            return;
        }

        // 3. Validate project directory
        File projectDir = resolveDirectory(config.getProjectDir());
        if (projectDir == null || !projectDir.exists() || !projectDir.isDirectory()) {
            ready = false;
            log.error("[ML Autostart] Configured ML project directory does not exist or is not a directory: '{}'. " +
                    "Please check 'ml-service.autostart.project-dir' in application.yaml.", config.getProjectDir());
            return;
        }

        // 4. Resolve and validate Python executable
        String pythonExec = resolvePythonExecutable(projectDir, config.getPythonPath());
        if (pythonExec == null) {
            ready = false;
            log.error("[ML Autostart] Could not locate a valid Python executable. Checked configured path ('{}') " +
                    "and virtual environments in '{}'. Automatic Flask startup failed.", config.getPythonPath(), projectDir.getAbsolutePath());
            return;
        }

        // 5. Validate entry point script
        File entryPointFile = new File(projectDir, config.getEntryPoint());
        if (!entryPointFile.exists()) {
            ready = false;
            log.error("[ML Autostart] Flask entry point script '{}' not found in '{}'.",
                    config.getEntryPoint(), projectDir.getAbsolutePath());
            return;
        }

        // 6. Launch the Flask process via ProcessBuilder
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonExec);
            command.add(config.getEntryPoint());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectDir);
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.environment().put("FLASK_RUN_PORT", String.valueOf(config.getPort()));

            flaskProcess = pb.start();
            startedByUs = true;
            long pid = flaskProcess.pid();
            log.info("[ML Autostart] Launched Flask ML service process (PID: {}, command: '{} {}', dir: '{}').",
                    pid, pythonExec, config.getEntryPoint(), projectDir.getAbsolutePath());

            // 7. Start async stream readers to log Flask stdout/stderr and prevent pipe deadlock
            startStreamLogger(flaskProcess.getInputStream(), "[Flask stdout]");
            startStreamLogger(flaskProcess.getErrorStream(), "[Flask stderr]");

        } catch (IOException e) {
            ready = false;
            log.error("[ML Autostart] Failed to start Flask process: {}", e.getMessage(), e);
            return;
        }

        // 8. Poll health check until ready or timeout
        pollHealthCheck(config.getHealthUrl(), config.getTimeoutSeconds(), config.getPollIntervalMs());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }

        if (startedByUs && flaskProcess != null && flaskProcess.isAlive()) {
            long pid = flaskProcess.pid();
            log.info("[ML Autostart] Terminating Flask ML service (PID: {})...", pid);
            try {
                // Terminate descendant processes first (e.g. Werkzeug reloader child processes)
                flaskProcess.descendants().forEach(handle -> {
                    try {
                        handle.destroy();
                    } catch (Exception ignored) {}
                });

                flaskProcess.destroy();
                boolean exited = flaskProcess.waitFor(5, TimeUnit.SECONDS);
                if (!exited) {
                    log.warn("[ML Autostart] Flask process did not terminate within 5 seconds; forcing kill.");
                    flaskProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                    flaskProcess.destroyForcibly();
                }
                log.info("[ML Autostart] Flask ML service stopped cleanly.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                flaskProcess.destroyForcibly();
            }
        } else if (!startedByUs) {
            log.info("[ML Autostart] Flask was not started by this Spring Boot instance; leaving external process untouched.");
        }

        ready = false;
        running = false;
        startedByUs = false;
        flaskProcess = null;
    }

    /**
     * Checks if the Flask health endpoint responds with HTTP 2xx and status UP.
     */
    public boolean checkHealth() {
        try {
            ResponseEntity<String> response = healthRestTemplate.getForEntity(
                    properties.getAutostart().getHealthUrl(), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isPortBound(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private File resolveDirectory(String dirPath) {
        if (dirPath == null || dirPath.isBlank()) {
            return null;
        }
        Path path = Paths.get(dirPath);
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path).normalize();
        }
        return path.toFile();
    }

    private String resolvePythonExecutable(File projectDir, String configuredPath) {
        // If explicitly configured, verify and return
        if (configuredPath != null && !configuredPath.isBlank()) {
            File explicit = new File(configuredPath);
            if (!explicit.isAbsolute()) {
                explicit = new File(projectDir, configuredPath);
            }
            if (explicit.exists() && explicit.canExecute()) {
                return explicit.getAbsolutePath();
            }
            log.warn("[ML Autostart] Configured Python path '{}' does not exist or is not executable. Falling back to auto-discovery.", configuredPath);
        }

        // Candidate paths within the project directory virtual environment
        String[] relativeCandidates = {
                // Windows virtual environments
                "venv/Scripts/python.exe",
                ".venv/Scripts/python.exe",
                "env/Scripts/python.exe",
                "Scripts/python.exe",
                // Unix / macOS virtual environments
                "venv/bin/python",
                ".venv/bin/python",
                "env/bin/python",
                "bin/python"
        };

        for (String rel : relativeCandidates) {
            File candidate = new File(projectDir, rel);
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }
        }

        // System fallback: check if 'python' or 'python3' is available on PATH
        String[] systemCandidates = {"python", "python3"};
        for (String cmd : systemCandidates) {
            try {
                Process p = new ProcessBuilder(cmd, "--version").start();
                if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return cmd;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private void startStreamLogger(InputStream inputStream, String prefix) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (prefix.contains("stderr")) {
                        log.warn("{} {}", prefix, line);
                    } else {
                        log.info("{} {}", prefix, line);
                    }
                }
            } catch (IOException ignored) {}
        }, "flask-stream-logger");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollHealthCheck(String healthUrl, int timeoutSeconds, long pollIntervalMs) {
        log.info("[ML Autostart] Polling Flask health-check endpoint at {} (timeout: {}s)...", healthUrl, timeoutSeconds);
        long start = System.currentTimeMillis();
        long maxWait = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - start < maxWait) {
            if (flaskProcess != null && !flaskProcess.isAlive()) {
                ready = false;
                log.error("[ML Autostart] Flask process terminated prematurely with exit code: {}. Aborting readiness poll.",
                        flaskProcess.exitValue());
                return;
            }

            if (checkHealth()) {
                ready = true;
                long elapsed = System.currentTimeMillis() - start;
                log.info("[ML Autostart] Flask ML service is HEALTHY and READY in {} ms (PID: {}).",
                        elapsed, flaskProcess != null ? flaskProcess.pid() : "unknown");
                return;
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ready = false;
                log.warn("[ML Autostart] Interrupted while polling Flask health.");
                return;
            }
        }

        ready = false;
        log.error("[ML Autostart] Flask ML service failed to become healthy within {} seconds.", timeoutSeconds);
    }
}
