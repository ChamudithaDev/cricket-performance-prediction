package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.external.PredictionHistoryItemDto;
import com.cricketprediction.backend.dto.external.PredictionMetadataDto;
import com.cricketprediction.backend.dto.external.PredictionRequestDto;
import com.cricketprediction.backend.dto.external.PredictionResponseDto;
import com.cricketprediction.backend.model.Prediction;
import com.cricketprediction.backend.model.User;
import com.cricketprediction.backend.repository.PredictionRepository;
import com.cricketprediction.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class PredictionService {

    private final RestTemplate restTemplate;
    private final String mlBaseUrl;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final PlayerAnalyticsService playerAnalyticsService;
    private final FlaskProcessManager flaskProcessManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public PredictionService(RestTemplate restTemplate,
                             @Value("${ml-service.base-url}") String mlBaseUrl,
                             PredictionRepository predictionRepository,
                             UserRepository userRepository,
                             PlayerAnalyticsService playerAnalyticsService,
                             FlaskProcessManager flaskProcessManager) {
        this.restTemplate = restTemplate;
        this.mlBaseUrl = mlBaseUrl.endsWith("/") ? mlBaseUrl.substring(0, mlBaseUrl.length() - 1) : mlBaseUrl;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.playerAnalyticsService = playerAnalyticsService;
        this.flaskProcessManager = flaskProcessManager;
    }

    /**
     * Overloaded constructor for unit testing without FlaskProcessManager.
     */
    public PredictionService(RestTemplate restTemplate,
                             String mlBaseUrl,
                             PredictionRepository predictionRepository,
                             UserRepository userRepository,
                             PlayerAnalyticsService playerAnalyticsService) {
        this(restTemplate, mlBaseUrl, predictionRepository, userRepository, playerAnalyticsService, null);
    }

    /**
     * Calls the external Flask ML service for Regression and Classification predictions,
     * validates input, extracts clean error messages, and stores the prediction in the audit repository.
     */
    public PredictionResponseDto predict(PredictionRequestDto request) {
        if (flaskProcessManager != null && !flaskProcessManager.isReady()) {
            throw new IllegalStateException("AI Prediction service is currently unavailable. Flask ML service is initializing or offline.");
        }

        if (request == null) {
            throw new IllegalArgumentException("Prediction request must not be null.");
        }
        if (request.getPlayer() == null || request.getPlayer().isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank.");
        }
        // Venue is optional: leave blank for general/neutral baseline without venue-specific bias
        if (request.getOpponent() == null || request.getOpponent().isBlank()) {
            throw new IllegalArgumentException("Opponent must not be blank.");
        }
        if (request.getMatch_type() == null || request.getMatch_type().isBlank()) {
            throw new IllegalArgumentException("Match type must not be blank.");
        }

        String url = mlBaseUrl + "/api/ml/predict";
        PredictionResponseDto response;
        try {
            response = restTemplate.postForObject(url, request, PredictionResponseDto.class);
        } catch (HttpStatusCodeException ex) {
            String errorMsg = extractErrorMessage(ex.getResponseBodyAsString(), ex.getMessage());
            log.warn("[Prediction] Flask service returned HTTP {}: {}", ex.getStatusCode(), errorMsg);
            throw new IllegalArgumentException(errorMsg);
        } catch (RestClientException ex) {
            log.error("[Prediction] Connection to ML service failed: {}", ex.getMessage());
            throw new IllegalStateException("AI Prediction service is currently unavailable. Please ensure Flask ML service is running.", ex);
        }

        if (response == null) {
            throw new IllegalStateException("Empty response received from AI Prediction service.");
        }

        // Ensure regression-derived tier is populated based on verified training quartiles
        if (response.getRegression_derived_tier() == null && response.getPredicted_runs() != null) {
            response.setRegression_derived_tier(deriveTierFromRuns(response.getPredicted_runs()));
        }

        // Persist prediction in database associated with current authenticated user
        User currentUser = getCurrentAuthenticatedUser();
        LocalDateTime now = LocalDateTime.now();
        String venueToSave = (response.getVenue() != null && !response.getVenue().isBlank())
                ? response.getVenue()
                : ((request.getVenue() != null && !request.getVenue().isBlank()) ? request.getVenue() : "Unspecified");

        Prediction prediction = Prediction.builder()
                .playerName(response.getPlayer() != null ? response.getPlayer() : request.getPlayer())
                .venue(venueToSave)
                .opponent(response.getOpponent() != null ? response.getOpponent() : request.getOpponent())
                .matchType(response.getMatch_type() != null ? response.getMatch_type() : request.getMatch_type())
                .predictedRuns(response.getPredicted_runs())
                .predictedTier(response.getPredicted_tier())
                .modelName(response.getModel_name() != null ? response.getModel_name() : "SVR")
                .classifierName(response.getClassifier_name() != null ? response.getClassifier_name() : "MLPClassifier")
                .predictionDate(now)
                .requestedBy(currentUser)
                .build();

        try {
            Prediction saved = predictionRepository.save(prediction);
            response.setId(saved.getId());
            response.setPrediction_date(saved.getPredictionDate().toString());
        } catch (Exception ex) {
            log.warn("[Prediction] Failed to persist prediction history: {}", ex.getMessage());
        }

        return response;
    }

    /**
     * Maps continuous predicted runs to historical training dataset quartiles:
     * - Poor: 0 - 1 runs (<= 25th percentile)
     * - Average: 2 - 9 runs (25th - 50th percentile)
     * - Good: 10 - 27 runs (50th - 75th percentile)
     * - Excellent: 28+ runs (> 75th percentile)
     */
    public static String deriveTierFromRuns(Double runs) {
        if (runs == null) return null;
        if (runs <= 1.0) return "Poor";
        if (runs <= 9.0) return "Average";
        if (runs <= 27.0) return "Good";
        return "Excellent";
    }

    /**
     * Retrieves recent prediction history for the authenticated user (or general recent if anonymous).
     */
    public List<PredictionHistoryItemDto> getPredictionHistory() {
        User user = getCurrentAuthenticatedUser();
        List<Prediction> list;
        if (user != null) {
            list = predictionRepository.findTop20ByRequestedByOrderByPredictionDateDesc(user);
        } else {
            list = predictionRepository.findTop20ByOrderByPredictionDateDesc();
        }

        return list.stream()
                .map(p -> PredictionHistoryItemDto.builder()
                        .id(p.getId())
                        .player(p.getPlayerName())
                        .opponent(p.getOpponent())
                        .venue(p.getVenue())
                        .matchType(p.getMatchType())
                        .predictedRuns(p.getPredictedRuns())
                        .predictedTier(p.getPredictedTier())
                        .modelName(p.getModelName())
                        .classifierName(p.getClassifierName())
                        .predictionDate(p.getPredictionDate())
                        .build())
                .toList();
    }

    /**
     * Gathers validated metadata for autocomplete and dropdown selectors.
     */
    public PredictionMetadataDto getPredictionMetadata() {
        List<String> players = playerAnalyticsService.listAvailablePlayers();
        List<String> venues = playerAnalyticsService.getAllVenues();
        List<String> opponents = playerAnalyticsService.getAllOppositions();
        List<String> matchTypes = List.of("T20I", "ODI", "TEST");

        return PredictionMetadataDto.builder()
                .players(players)
                .venues(venues)
                .opponents(opponents)
                .matchTypes(matchTypes)
                .build();
    }

    private User getCurrentAuthenticatedUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            if (auth.getPrincipal() instanceof User u) return u;
            if (auth.getName() != null && !auth.getName().equalsIgnoreCase("anonymousUser")) {
                return userRepository.findByEmail(auth.getName()).orElse(null);
            }
        } catch (Exception ex) {
            log.debug("[Prediction] Unable to resolve authenticated user: {}", ex.getMessage());
        }
        return null;
    }

    private String extractErrorMessage(String body, String fallback) {
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(body);
                if (node.has("error")) {
                    return node.get("error").asText();
                }
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
