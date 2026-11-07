package com.cricketprediction.backend.service;

import com.cricketprediction.backend.config.FlaskServiceProperties;
import com.cricketprediction.backend.dto.analytics.PlayerAnalyticsResponse;
import com.cricketprediction.backend.dto.analytics.PlayerComparisonResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service to orchestrate the generation of native Excel (.xlsx) workbooks containing
 * editable DrawingML charts for Player Performance Analysis and Head-to-Head Comparison.
 *
 * Architecture:
 * 1. Collects verified analytics dataset from PlayerAnalyticsService.
 * 2. Transmits payload to Flask Python ML Service (/api/ml/reports) which uses xlsxwriter.
 * 3. Includes automatic local Python CLI ProcessBuilder fallback if Flask daemon is offline.
 * 4. Returns byte[] payload for streaming to clients with exact filename conventions.
 */
@Service
@Slf4j
public class ExcelReportExportService {

    private final PlayerAnalyticsService analyticsService;
    private final RestTemplate restTemplate;
    private final String mlBaseUrl;
    private final FlaskServiceProperties flaskProperties;
    private final ObjectMapper objectMapper;

    public ExcelReportExportService(
            PlayerAnalyticsService analyticsService,
            RestTemplate restTemplate,
            @Value("${ml-service.base-url:http://localhost:5000}") String mlBaseUrl,
            FlaskServiceProperties flaskProperties,
            ObjectMapper objectMapper) {
        this.analyticsService = analyticsService;
        this.restTemplate = restTemplate;
        this.mlBaseUrl = mlBaseUrl.endsWith("/") ? mlBaseUrl.substring(0, mlBaseUrl.length() - 1) : mlBaseUrl;
        this.flaskProperties = flaskProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a native Excel workbook containing editable DrawingML charts for a single player analysis.
     */
    public byte[] exportPlayerAnalysis(String name, String format, int recentN) {
        PlayerAnalyticsResponse analytics = analyticsService.getAnalytics(name, format, recentN);
        if (analytics == null) {
            throw new IllegalArgumentException("No analytics found for player: " + name);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("analytics", analytics);
        payload.put("format", format != null ? format : "ALL");
        payload.put("recentN", recentN > 0 ? recentN : 15);

        // 1. Try Flask HTTP endpoint
        try {
            String url = mlBaseUrl + "/api/ml/reports/player-analysis";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(
                    MediaType.APPLICATION_OCTET_STREAM,
                    MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, request, byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().length > 0) {
                log.info("[ExcelExport] Successfully generated native Excel report via Flask HTTP for player: {}", name);
                return response.getBody();
            }
        } catch (Exception ex) {
            log.warn("[ExcelExport] Flask HTTP report call failed ({}). Attempting local Python CLI fallback...", ex.getMessage());
        }

        // 2. Fallback to CLI
        return executePythonGeneratorCli("player", payload, format, recentN, null, null);
    }

    /**
     * Generates a native Excel workbook containing editable DrawingML charts for a head-to-head comparison.
     */
    public byte[] exportPlayerComparison(String player1, String player2, String format, int recentN, String opposition, String venue) {
        PlayerComparisonResponse comparison = analyticsService.comparePlayers(player1, player2, format, recentN, opposition, venue);
        if (comparison == null) {
            throw new IllegalArgumentException("Could not generate comparison for " + player1 + " and " + player2);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("comparison", comparison);
        payload.put("format", format != null ? format : "ALL");
        payload.put("recentN", recentN > 0 ? recentN : 15);
        if (opposition != null && !opposition.isBlank()) payload.put("opposition", opposition);
        if (venue != null && !venue.isBlank()) payload.put("venue", venue);

        // 1. Try Flask HTTP endpoint
        try {
            String url = mlBaseUrl + "/api/ml/reports/player-comparison";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(
                    MediaType.APPLICATION_OCTET_STREAM,
                    MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, request, byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().length > 0) {
                log.info("[ExcelExport] Successfully generated native Excel comparison report via Flask HTTP for {} vs {}", player1, player2);
                return response.getBody();
            }
        } catch (Exception ex) {
            log.warn("[ExcelExport] Flask HTTP comparison report call failed ({}). Attempting local Python CLI fallback...", ex.getMessage());
        }

        // 2. Fallback to CLI
        return executePythonGeneratorCli("compare", payload, format, recentN, opposition, venue);
    }

    public String getPlayerAnalysisFilename(String playerName, String format) {
        String cleanName = sanitizeFilename(playerName);
        String cleanFormat = (format != null && !format.isBlank()) ? format.toUpperCase() : "ALL";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.format("CricketAI_Player_Analysis_%s_%s_%s.xlsx", cleanName, cleanFormat, dateStr);
    }

    public String getPlayerComparisonFilename(String player1, String player2, String format) {
        String cleanP1 = sanitizeFilename(player1);
        String cleanP2 = sanitizeFilename(player2);
        String cleanFormat = (format != null && !format.isBlank()) ? format.toUpperCase() : "ALL";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.format("CricketAI_Player_Comparison_%s_vs_%s_%s_%s.xlsx", cleanP1, cleanP2, cleanFormat, dateStr);
    }

    private String sanitizeFilename(String s) {
        if (s == null) return "Player";
        return s.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private byte[] executePythonGeneratorCli(
            String type,
            Map<String, Object> payload,
            String format,
            int recentN,
            String opposition,
            String venue) {

        File projectDir = resolveProjectDirectory();
        String pythonExec = resolvePythonExecutable(projectDir);

        File tempInput = null;
        File tempOutput = null;

        try {
            tempInput = File.createTempFile("cricketai_export_in_", ".json");
            tempOutput = File.createTempFile("cricketai_export_out_", ".xlsx");

            // Write payload JSON
            objectMapper.writeValue(tempInput, payload);

            List<String> cmd = new ArrayList<>();
            cmd.add(pythonExec);
            cmd.add("-m");
            cmd.add("api.excel_report_generator");
            cmd.add("--type");
            cmd.add(type);
            cmd.add("--input");
            cmd.add(tempInput.getAbsolutePath());
            cmd.add("--output");
            cmd.add(tempOutput.getAbsolutePath());
            cmd.add("--format");
            cmd.add(format != null ? format : "ALL");
            cmd.add("--recentN");
            cmd.add(String.valueOf(recentN > 0 ? recentN : 15));

            if (opposition != null && !opposition.isBlank()) {
                cmd.add("--opposition");
                cmd.add(opposition);
            }
            if (venue != null && !venue.isBlank()) {
                cmd.add("--venue");
                cmd.add(venue);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projectDir);
            pb.redirectErrorStream(true);

            log.info("[ExcelExport CLI] Executing Python generator: {}", String.join(" ", cmd));
            Process process = pb.start();

            StringBuilder outputLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLog.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Python Excel generator process timed out after 45 seconds.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("[ExcelExport CLI] Python generator failed with exit code {}. Output:\n{}", exitCode, outputLog);
                throw new IllegalStateException("Python Excel generator execution failed: " + outputLog);
            }

            if (!tempOutput.exists() || tempOutput.length() == 0) {
                throw new IllegalStateException("Python Excel generator completed but output file is missing or empty.");
            }

            log.info("[ExcelExport CLI] Successfully generated workbook ({} bytes) via CLI.", tempOutput.length());
            return Files.readAllBytes(tempOutput.toPath());

        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[ExcelExport CLI] Exception during Python report generation: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to generate Excel report: " + ex.getMessage(), ex);
        } finally {
            if (tempInput != null && tempInput.exists()) {
                try { Files.deleteIfExists(tempInput.toPath()); } catch (Exception ignored) {}
            }
            if (tempOutput != null && tempOutput.exists()) {
                try { Files.deleteIfExists(tempOutput.toPath()); } catch (Exception ignored) {}
            }
        }
    }

    private File resolveProjectDirectory() {
        String configuredDir = flaskProperties.getAutostart().getProjectDir();
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = "../ml-service";
        }
        Path path = Paths.get(configuredDir);
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path).normalize();
        }
        File dir = path.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            // Also test relative to current directory if running in IDE
            File candidate = new File("ml-service");
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate.getAbsoluteFile();
            }
            File candidate2 = new File("../ml-service");
            if (candidate2.exists() && candidate2.isDirectory()) {
                return candidate2.getAbsoluteFile();
            }
        }
        return dir;
    }

    private String resolvePythonExecutable(File projectDir) {
        String configured = flaskProperties.getAutostart().getPythonPath();
        if (configured != null && !configured.isBlank()) {
            File f = new File(configured);
            if (!f.isAbsolute()) {
                f = new File(projectDir, configured);
            }
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        String[] venvRelatives = {
                "venv/Scripts/python.exe",
                ".venv/Scripts/python.exe",
                "env/Scripts/python.exe",
                "Scripts/python.exe",
                "venv/bin/python",
                ".venv/bin/python",
                "env/bin/python",
                "bin/python"
        };

        for (String rel : venvRelatives) {
            File cand = new File(projectDir, rel);
            if (cand.exists()) {
                return cand.getAbsolutePath();
            }
        }

        return "python";
    }
}
