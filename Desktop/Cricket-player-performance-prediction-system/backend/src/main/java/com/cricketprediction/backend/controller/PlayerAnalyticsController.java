package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.analytics.PlayerAnalyticsResponse;
import com.cricketprediction.backend.service.ExcelReportExportService;
import com.cricketprediction.backend.service.PlayerAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Player Analytics API — serves historical performance analytics derived
 * from batting_performance.csv and bowling_performance.csv.
 *
 * Base path: /api/analytics
 *
 * Endpoints:
 *   GET /api/analytics/player?name=Virat+Kohli&format=ODI&recentN=10
 *       Returns full analytics for that player, filtered to the given format.
 *       format defaults to "ALL" (no filtering).
 *
 *   GET /api/analytics/players
 *       Lists all player names available in the historical dataset.
 *
 * All endpoints require Bearer JWT authentication.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class PlayerAnalyticsController {

    private final PlayerAnalyticsService analyticsService;
    private final ExcelReportExportService exportService;
    private final com.cricketprediction.backend.service.PlayerStrengthsService playerStrengthsService;

    /**
     * GET /api/analytics/player-strengths?name=...&format=ALL
     *
     * Returns head-to-head batter vs bowler matchups, bowler vs batter matchups,
     * and match-phase performance (Powerplay, Middle, Death overs).
     *
     * @param name   player name to look up
     * @param format cricket format: ALL, ODI, T20I, or TEST (default ALL)
     * @return 200 OK with PlayerStrengthsResponse, or 400 Bad Request on unknown player
     */
    @GetMapping("/player-strengths")
    public ResponseEntity<?> getPlayerStrengths(
            @RequestParam String name,
            @RequestParam(defaultValue = "ALL") String format) {
        try {
            com.cricketprediction.backend.dto.analytics.PlayerStrengthsResponse response =
                    playerStrengthsService.getPlayerStrengths(name, format);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/analytics/player?name=...&format=ODI&recentN=10
     *
     * @param name    player name to look up (case-insensitive, partial match)
     * @param format  cricket format: ODI, T20I, TEST, or ALL (default ALL)
     * @param recentN number of recent innings to return in recent-form (default 15)
     * @return 200 OK with PlayerAnalyticsResponse, or 404 if no data found, 400 on ambiguous name
     */
    @GetMapping("/player")
    public ResponseEntity<?> getPlayerAnalytics(
            @RequestParam String name,
            @RequestParam(defaultValue = "ALL") String format,
            @RequestParam(defaultValue = "15") int recentN) {

        try {
            PlayerAnalyticsResponse response = analyticsService.getAnalytics(name, format, recentN);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/analytics/players
     *
     * @return sorted list of player names in the historical dataset
     */
    @GetMapping("/players")
    public ResponseEntity<List<String>> listPlayers() {
        return ResponseEntity.ok(analyticsService.listAvailablePlayers());
    }

    /**
     * GET /api/analytics/compare?player1=...&player2=...&format=ALL&recentN=15&opposition=...&venue=...
     *
     * @param player1    first player name (fuzzy matched)
     * @param player2    second player name (fuzzy matched)
     * @param format     cricket format: ALL, ODI, T20I, or TEST (default ALL)
     * @param recentN    number of recent matches for trendlines (default 15)
     * @param opposition optional opposition name to filter comparison
     * @param venue      optional venue name to filter comparison
     * @return 200 OK with PlayerComparisonResponse, or 400 Bad Request on invalid arguments
     */
    @GetMapping("/compare")
    public ResponseEntity<?> comparePlayers(
            @RequestParam String player1,
            @RequestParam String player2,
            @RequestParam(defaultValue = "ALL") String format,
            @RequestParam(defaultValue = "15") int recentN,
            @RequestParam(required = false) String opposition,
            @RequestParam(required = false) String venue) {
        try {
            com.cricketprediction.backend.dto.analytics.PlayerComparisonResponse response =
                    analyticsService.comparePlayers(player1, player2, format, recentN, opposition, venue);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/analytics/export/player?name=...&format=ALL&recentN=15
     *
     * Downloads a professional Excel (.xlsx) workbook containing editable DrawingML charts.
     */
    @GetMapping("/export/player")
    public ResponseEntity<?> exportPlayerReport(
            @RequestParam String name,
            @RequestParam(defaultValue = "ALL") String format,
            @RequestParam(defaultValue = "15") int recentN) {
        try {
            byte[] fileBytes = exportService.exportPlayerAnalysis(name, format, recentN);
            String filename = exportService.getPlayerAnalysisFilename(name, format);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(fileBytes);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate Excel report: " + ex.getMessage()));
        }
    }

    /**
     * GET /api/analytics/export/compare?player1=...&player2=...&format=ALL&recentN=15&opposition=...&venue=...
     *
     * Downloads a head-to-head comparison Excel (.xlsx) workbook containing editable DrawingML charts.
     */
    @GetMapping("/export/compare")
    public ResponseEntity<?> exportCompareReport(
            @RequestParam String player1,
            @RequestParam String player2,
            @RequestParam(defaultValue = "ALL") String format,
            @RequestParam(defaultValue = "15") int recentN,
            @RequestParam(required = false) String opposition,
            @RequestParam(required = false) String venue) {
        try {
            byte[] fileBytes = exportService.exportPlayerComparison(player1, player2, format, recentN, opposition, venue);
            String filename = exportService.getPlayerComparisonFilename(player1, player2, format);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(fileBytes);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate comparison Excel report: " + ex.getMessage()));
        }
    }
}
