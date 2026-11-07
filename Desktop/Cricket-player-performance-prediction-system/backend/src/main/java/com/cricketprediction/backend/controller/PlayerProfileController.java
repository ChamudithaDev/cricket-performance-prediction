package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.PlayerProfileResponse;
import com.cricketprediction.backend.dto.external.CricketPlayerSearchItem;
import com.cricketprediction.backend.service.PlayerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Player Profile API — integrates with external Cricket Data API.
 *
 * Base path: /api/player-profiles
 *
 * Endpoints:
 *   GET  /api/player-profiles/search?name=...  — search external players (no auto-sync)
 *   POST /api/player-profiles/sync/{externalId} — sync/import external player & career stats
 *   GET  /api/player-profiles/{id}             — get complete local profile and stats
 *
 * All endpoints require Bearer JWT authentication.
 */
@RestController
@RequestMapping("/api/player-profiles")
@RequiredArgsConstructor
public class PlayerProfileController {

    private final PlayerProfileService playerProfileService;

    /**
     * GET /api/player-profiles/search?name=Virat%20Kohli
     *
     * Searches external Cricket Data API.
     *
     * @param name player search term
     * @return 200 OK with list of external player items
     */
    @GetMapping("/search")
    public ResponseEntity<List<CricketPlayerSearchItem>> searchExternalPlayers(@RequestParam(required = false) String name) {
        return ResponseEntity.ok(playerProfileService.searchExternalPlayers(name));
    }

    /**
     * POST /api/player-profiles/sync/{externalPlayerId}
     *
     * Fetches external player profile & career stats and imports/updates into the local database.
     *
     * @param externalPlayerId external API UUID
     * @return 200 OK with complete PlayerProfileResponse
     */
    @PostMapping("/sync/{externalPlayerId}")
    public ResponseEntity<PlayerProfileResponse> syncPlayer(@PathVariable String externalPlayerId) {
        return ResponseEntity.ok(playerProfileService.syncPlayer(externalPlayerId));
    }

    /**
     * GET /api/player-profiles/{id}
     *
     * Returns complete locally stored player profile and career statistics.
     *
     * @param id local player ID
     * @return 200 OK with PlayerProfileResponse, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlayerProfileResponse> getPlayerProfile(@PathVariable Long id) {
        return ResponseEntity.ok(playerProfileService.getPlayerProfile(id));
    }
}
