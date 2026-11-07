package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.toss.*;
import com.cricketprediction.backend.service.TossDecisionService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TossDecisionControllerTest {

    @Mock
    private TossDecisionService tossDecisionService;

    @InjectMocks
    private TossDecisionController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    @DisplayName("GET /api/analytics/toss-decision: Should return 200 with TossDecisionResponse")
    void testGetTossDecisionSuccess() throws Exception {
        TossDecisionResponse mockResponse = TossDecisionResponse.builder()
                .venue("R Premadasa Stadium, Colombo")
                .format("ODI")
                .referenceDate("2026-07-30")
                .windowStartDate("2024-07-30")
                .windowEndDate("2026-07-30")
                .totalEligibleMatches(10)
                .decisiveMatches(9)
                .batFirstWins(8)
                .chaseWins(1)
                .batFirstWinRate(88.9)
                .chaseWinRate(11.1)
                .percentagePointDifference(77.8)
                .recommendation(TossRecommendation.BAT_FIRST)
                .recommendationLabel("Bat First")
                .explanation("Historical advantage favors batting first.")
                .excludedBreakdown(TossExcludedBreakdown.builder().tieCount(1).totalExcludedCount(1).build())
                .warnings(List.of())
                .build();

        when(tossDecisionService.analyzeTossDecision(eq("R Premadasa Stadium, Colombo"), eq("ODI")))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/analytics/toss-decision")
                        .param("venue", "R Premadasa Stadium, Colombo")
                        .param("format", "ODI")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value("R Premadasa Stadium, Colombo"))
                .andExpect(jsonPath("$.format").value("ODI"))
                .andExpect(jsonPath("$.decisiveMatches").value(9))
                .andExpect(jsonPath("$.batFirstWinRate").value(88.9))
                .andExpect(jsonPath("$.chaseWinRate").value(11.1))
                .andExpect(jsonPath("$.recommendation").value("BAT_FIRST"))
                .andExpect(jsonPath("$.recommendationLabel").value("Bat First"));
    }

    @Test
    @DisplayName("GET /api/analytics/toss-decision: Should return 400 on service IllegalArgumentException")
    void testGetTossDecisionBadRequest() throws Exception {
        when(tossDecisionService.analyzeTossDecision(eq("Bad Venue"), eq("INVALID")))
                .thenThrow(new IllegalArgumentException("Unsupported format 'INVALID'"));

        mockMvc.perform(get("/api/analytics/toss-decision")
                        .param("venue", "Bad Venue")
                        .param("format", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported format 'INVALID'"));
    }

    @Test
    @DisplayName("GET /api/analytics/toss-decision/venues: Should return 200 with available venues")
    void testGetVenues() throws Exception {
        when(tossDecisionService.getAvailableVenues("ODI"))
                .thenReturn(List.of("Melbourne Cricket Ground", "R Premadasa Stadium, Colombo"));

        mockMvc.perform(get("/api/analytics/toss-decision/venues")
                        .param("format", "ODI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("ODI"))
                .andExpect(jsonPath("$.totalVenues").value(2))
                .andExpect(jsonPath("$.venues[0]").value("Melbourne Cricket Ground"))
                .andExpect(jsonPath("$.venues[1]").value("R Premadasa Stadium, Colombo"));
    }

    @Test
    @DisplayName("GET /api/analytics/toss-decision/formats: Should return supported formats")
    void testGetFormats() throws Exception {
        mockMvc.perform(get("/api/analytics/toss-decision/formats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ODI"))
                .andExpect(jsonPath("$[1]").value("T20I"))
                .andExpect(jsonPath("$[2]").value("TEST"));
    }
}
