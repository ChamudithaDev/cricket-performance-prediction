package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.analytics.PlayerAnalyticsResponse;
import com.cricketprediction.backend.dto.analytics.PlayerComparisonResponse;
import com.cricketprediction.backend.dto.analytics.PlayerStrengthsResponse;
import com.cricketprediction.backend.service.PlayerAnalyticsService;
import com.cricketprediction.backend.service.PlayerStrengthsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlayerAnalyticsControllerTest {

    @Mock
    private PlayerAnalyticsService analyticsService;

    @Mock
    private PlayerStrengthsService playerStrengthsService;

    @InjectMocks
    private PlayerAnalyticsController analyticsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(analyticsController)
                .build();
    }

    @Test
    @DisplayName("GET /api/analytics/compare: Should return 200 with PlayerComparisonResponse")
    void testCompareSuccess() throws Exception {
        PlayerComparisonResponse response = PlayerComparisonResponse.builder()
                .player1Name("V Kohli")
                .player2Name("BM Duckett")
                .format("ALL")
                .comparisonScope("BATTING_ONLY")
                .scopeExplanation("Both players are batsmen.")
                .metricAdvantages(Map.of("runs", "player1"))
                .build();

        when(analyticsService.comparePlayers(eq("V Kohli"), eq("BM Duckett"), eq("ALL"), eq(15), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/analytics/compare")
                        .param("player1", "V Kohli")
                        .param("player2", "BM Duckett")
                        .param("format", "ALL")
                        .param("recentN", "15")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player1Name").value("V Kohli"))
                .andExpect(jsonPath("$.player2Name").value("BM Duckett"))
                .andExpect(jsonPath("$.comparisonScope").value("BATTING_ONLY"))
                .andExpect(jsonPath("$.metricAdvantages.runs").value("player1"));
    }

    @Test
    @DisplayName("GET /api/analytics/compare: Should return 400 when comparison throws IllegalArgumentException")
    void testCompareSamePlayerError() throws Exception {
        when(analyticsService.comparePlayers(eq("V Kohli"), eq("V Kohli"), eq("ALL"), eq(15), any(), any()))
                .thenThrow(new IllegalArgumentException("Cannot compare a player with themselves: \"V Kohli\"."));

        mockMvc.perform(get("/api/analytics/compare")
                        .param("player1", "V Kohli")
                        .param("player2", "V Kohli")
                        .param("format", "ALL")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot compare a player with themselves: \"V Kohli\"."));
    }

    @Test
    @DisplayName("GET /api/analytics/players: Should return 200 with list of players")
    void testListPlayers() throws Exception {
        when(analyticsService.listAvailablePlayers()).thenReturn(List.of("AU Rashid", "BM Duckett", "V Kohli"));

        mockMvc.perform(get("/api/analytics/players")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("AU Rashid"))
                .andExpect(jsonPath("$[2]").value("V Kohli"));
    }

    @Test
    @DisplayName("GET /api/analytics/player: Should return 200 with PlayerAnalyticsResponse")
    void testGetPlayer() throws Exception {
        PlayerAnalyticsResponse response = PlayerAnalyticsResponse.builder()
                .playerName("V Kohli")
                .role("BATSMAN")
                .selectedFormat("ALL")
                .build();

        when(analyticsService.getAnalytics(eq("V Kohli"), eq("ALL"), eq(15)))
                .thenReturn(response);

        mockMvc.perform(get("/api/analytics/player")
                        .param("name", "V Kohli")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("V Kohli"))
                .andExpect(jsonPath("$.role").value("BATSMAN"));
    }

    @Test
    @DisplayName("GET /api/analytics/player: Should return enriched role-based summaries and distributions")
    void testGetPlayerEnrichedFields() throws Exception {
        PlayerAnalyticsResponse response = PlayerAnalyticsResponse.builder()
                .playerName("V Kohli")
                .role("BATSMAN")
                .selectedFormat("ODI")
                .battingSummary(com.cricketprediction.backend.dto.analytics.BattingAnalyticsSummary.builder()
                        .innings(50)
                        .totalRuns(2500)
                        .strikeRate(93.4)
                        .build())
                .scoreDistribution(com.cricketprediction.backend.dto.analytics.ScoreDistribution.builder()
                        .centuries(12)
                        .fifties(15)
                        .build())
                .build();

        when(analyticsService.getAnalytics(eq("V Kohli"), eq("ODI"), eq(10)))
                .thenReturn(response);

        mockMvc.perform(get("/api/analytics/player")
                        .param("name", "V Kohli")
                        .param("format", "ODI")
                        .param("recentN", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("V Kohli"))
                .andExpect(jsonPath("$.selectedFormat").value("ODI"))
                .andExpect(jsonPath("$.battingSummary.totalRuns").value(2500))
                .andExpect(jsonPath("$.scoreDistribution.centuries").value(12));
    }

    @Test
    @DisplayName("GET /api/analytics/compare: Should pass opposition and venue parameters to service")
    void testCompareWithContextFilters() throws Exception {
        PlayerComparisonResponse response = PlayerComparisonResponse.builder()
                .player1Name("BM Duckett")
                .player2Name("PD Salt")
                .format("ALL")
                .comparisonScope("BATTING_ONLY")
                .build();

        when(analyticsService.comparePlayers(eq("BM Duckett"), eq("PD Salt"), eq("ALL"), eq(10), eq("Australia"), eq("MCG")))
                .thenReturn(response);

        mockMvc.perform(get("/api/analytics/compare")
                        .param("player1", "BM Duckett")
                        .param("player2", "PD Salt")
                        .param("format", "ALL")
                        .param("recentN", "10")
                        .param("opposition", "Australia")
                        .param("venue", "MCG")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player1Name").value("BM Duckett"))
                .andExpect(jsonPath("$.player2Name").value("PD Salt"));
    }

    @Test
    @DisplayName("GET /api/analytics/player-strengths: Should return 200 with PlayerStrengthsResponse")
    void testGetPlayerStrengthsSuccess() throws Exception {
        PlayerStrengthsResponse response = PlayerStrengthsResponse.builder()
                .playerName("V Kohli")
                .selectedFormat("ODI")
                .detectedRole("BATSMAN")
                .hasBattingMatchups(true)
                .hasBowlingMatchups(false)
                .hasPhaseData(true)
                .totalBowlersFaced(45)
                .build();

        when(playerStrengthsService.getPlayerStrengths("V Kohli", "ODI")).thenReturn(response);

        mockMvc.perform(get("/api/analytics/player-strengths")
                        .param("name", "V Kohli")
                        .param("format", "ODI")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("V Kohli"))
                .andExpect(jsonPath("$.selectedFormat").value("ODI"))
                .andExpect(jsonPath("$.detectedRole").value("BATSMAN"))
                .andExpect(jsonPath("$.hasBattingMatchups").value(true))
                .andExpect(jsonPath("$.totalBowlersFaced").value(45));
    }

    @Test
    @DisplayName("GET /api/analytics/player-strengths: Should return 400 when player is unknown")
    void testGetPlayerStrengthsUnknownPlayer() throws Exception {
        when(playerStrengthsService.getPlayerStrengths("Unknown Fake", "ALL"))
                .thenThrow(new IllegalArgumentException("No player found matching: 'Unknown Fake'"));

        mockMvc.perform(get("/api/analytics/player-strengths")
                        .param("name", "Unknown Fake")
                        .param("format", "ALL")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No player found matching: 'Unknown Fake'"));
    }
}
