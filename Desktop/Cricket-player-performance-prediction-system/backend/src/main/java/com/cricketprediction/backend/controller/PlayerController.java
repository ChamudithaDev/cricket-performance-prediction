package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.PlayerResponse;
import com.cricketprediction.backend.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Player Management API — all endpoints require a valid JWT (enforced by SecurityConfig).
 *
 * Base path: /api/players
 *
 * Endpoints:
 *   GET /api/players                  — paginated list of all players (page, size, sort)
 *   GET /api/players/{id}             — single player by primary-key ID
 *   GET /api/players/search?name=...  — case-insensitive partial-name search
 *
 * Authentication:
 *   All endpoints require:  Authorization: Bearer <JWT>
 *   Unauthenticated requests → 401 Forbidden (enforced by Spring Security)
 *
 * Error responses:
 *   404  { "error": "Player not found with id: X" }
 *   401  (no body — returned by Spring Security before the controller is reached)
 */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    /**
     * GET /api/players
     *
     * Returns all players sorted by name ascending.
     *
     * @return 200 OK with List<PlayerResponse>
     */
    @GetMapping
    public ResponseEntity<List<PlayerResponse>> getAllPlayers() {
        return ResponseEntity.ok(playerService.getAllPlayers());
    }

    /**
     * GET /api/players/{id}
     *
     * @param id player primary key
     * @return 200 OK with PlayerResponse, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponse> getPlayerById(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.getPlayerById(id));
    }

    /**
     * GET /api/players/search?name=Kohli
     *
     * Case-insensitive partial-name match.
     * A blank name parameter returns an empty list (not an error).
     *
     * @param name partial player name
     * @return 200 OK with List<PlayerResponse>
     */
    @GetMapping("/search")
    public ResponseEntity<List<PlayerResponse>> searchPlayers(
            @RequestParam(required = false, defaultValue = "") String name) {
        return ResponseEntity.ok(playerService.searchByName(name));
    }
}
