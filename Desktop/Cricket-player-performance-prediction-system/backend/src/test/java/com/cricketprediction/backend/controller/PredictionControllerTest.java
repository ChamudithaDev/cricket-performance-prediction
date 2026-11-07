package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.external.PredictionHistoryItemDto;
import com.cricketprediction.backend.dto.external.PredictionMetadataDto;
import com.cricketprediction.backend.dto.external.PredictionRequestDto;
import com.cricketprediction.backend.dto.external.PredictionResponseDto;
import com.cricketprediction.backend.service.PredictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PredictionControllerTest {

    @Mock
    private PredictionService predictionService;

    @InjectMocks
    private PredictionController predictionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(predictionController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/predictions: Should return 200 with regression and classification results")
    void testPredictSuccess() throws Exception {
        PredictionRequestDto request = new PredictionRequestDto("V Kohli", "Eden Gardens", "Australia", "T20I");

        PredictionResponseDto response = PredictionResponseDto.builder()
                .id(10L)
                .player("V Kohli")
                .opponent("Australia")
                .venue("Eden Gardens")
                .match_type("t20i")
                .predicted_runs(38.0)
                .regression_derived_tier("Excellent")
                .predicted_tier("Good")
                .tier_probabilities(Map.of("Poor", 0.05, "Average", 0.15, "Good", 0.60, "Excellent", 0.20))
                .model_name("SVR")
                .classifier_name("MLPClassifier")
                .build();

        when(predictionService.predict(any(PredictionRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player").value("V Kohli"))
                .andExpect(jsonPath("$.predicted_runs").value(38.0))
                .andExpect(jsonPath("$.regression_derived_tier").value("Excellent"))
                .andExpect(jsonPath("$.predicted_tier").value("Good"))
                .andExpect(jsonPath("$.tier_probabilities.Good").value(0.60))
                .andExpect(jsonPath("$.model_name").value("SVR"))
                .andExpect(jsonPath("$.classifier_name").value("MLPClassifier"));
    }

    @Test
    @DisplayName("POST /api/predictions: Should return 200 when venue is omitted (optional)")
    void testPredictWithoutVenueSuccess() throws Exception {
        PredictionRequestDto requestWithoutVenue = new PredictionRequestDto("V Kohli", null, "Australia", "T20I");

        PredictionResponseDto response = PredictionResponseDto.builder()
                .id(12L)
                .player("V Kohli")
                .opponent("Australia")
                .venue(null)
                .match_type("t20i")
                .predicted_runs(23.5)
                .regression_derived_tier("Good")
                .predicted_tier("Excellent")
                .tier_probabilities(Map.of("Poor", 0.003, "Average", 0.201, "Good", 0.368, "Excellent", 0.428))
                .model_name("SVR")
                .classifier_name("MLPClassifier")
                .build();

        when(predictionService.predict(any(PredictionRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestWithoutVenue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player").value("V Kohli"))
                .andExpect(jsonPath("$.predicted_runs").value(23.5))
                .andExpect(jsonPath("$.regression_derived_tier").value("Good"))
                .andExpect(jsonPath("$.predicted_tier").value("Excellent"))
                .andExpect(jsonPath("$.tier_probabilities.Excellent").value(0.428));
    }

    @Test
    @DisplayName("POST /api/predictions: Should return 400 when service throws IllegalArgumentException")
    void testPredictIllegalArgumentException() throws Exception {
        PredictionRequestDto request = new PredictionRequestDto("Fake Player", "Eden Gardens", "Australia", "T20I");

        when(predictionService.predict(any(PredictionRequestDto.class)))
                .thenThrow(new IllegalArgumentException("No historical data found for 'Fake Player'."));

        mockMvc.perform(post("/api/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No historical data found for 'Fake Player'."));
    }

    @Test
    @DisplayName("GET /api/predictions/history: Should return 200 with list of prediction history")
    void testGetHistory() throws Exception {
        PredictionHistoryItemDto item = PredictionHistoryItemDto.builder()
                .id(1L)
                .player("V Kohli")
                .opponent("Australia")
                .venue("Eden Gardens")
                .matchType("t20i")
                .predictedRuns(45.0)
                .predictedTier("Excellent")
                .modelName("SVR")
                .classifierName("MLPClassifier")
                .predictionDate(LocalDateTime.now())
                .build();

        when(predictionService.getPredictionHistory()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/predictions/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].player").value("V Kohli"))
                .andExpect(jsonPath("$[0].predictedTier").value("Excellent"))
                .andExpect(jsonPath("$[0].predictedRuns").value(45.0));
    }

    @Test
    @DisplayName("GET /api/predictions/meta: Should return 200 with metadata collections")
    void testGetMeta() throws Exception {
        PredictionMetadataDto meta = PredictionMetadataDto.builder()
                .players(List.of("V Kohli"))
                .venues(List.of("Eden Gardens"))
                .opponents(List.of("Australia"))
                .matchTypes(List.of("T20I", "ODI", "TEST"))
                .build();

        when(predictionService.getPredictionMetadata()).thenReturn(meta);

        mockMvc.perform(get("/api/predictions/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0]").value("V Kohli"))
                .andExpect(jsonPath("$.venues[0]").value("Eden Gardens"))
                .andExpect(jsonPath("$.opponents[0]").value("Australia"))
                .andExpect(jsonPath("$.matchTypes[0]").value("T20I"));
    }
}
