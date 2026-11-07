package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.external.PredictionHistoryItemDto;
import com.cricketprediction.backend.dto.external.PredictionMetadataDto;
import com.cricketprediction.backend.dto.external.PredictionRequestDto;
import com.cricketprediction.backend.dto.external.PredictionResponseDto;
import com.cricketprediction.backend.model.Prediction;
import com.cricketprediction.backend.model.Role;
import com.cricketprediction.backend.model.User;
import com.cricketprediction.backend.repository.PredictionRepository;
import com.cricketprediction.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlayerAnalyticsService playerAnalyticsService;

    private PredictionService predictionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(
                restTemplate,
                "http://localhost:5000",
                predictionRepository,
                userRepository,
                playerAnalyticsService
        );

        testUser = User.builder()
                .id(1L)
                .email("testuser@example.com")
                .fullName("Test Cricketer")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("predict: Should return regression and classification output and persist prediction")
    void testPredictSuccess() {
        // Authenticate context
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        PredictionRequestDto request = new PredictionRequestDto("V Kohli", "Eden Gardens", "Australia", "T20I");

        PredictionResponseDto flaskResponse = PredictionResponseDto.builder()
                .player("V Kohli")
                .opponent("Australia")
                .venue("Eden Gardens")
                .match_type("t20i")
                .predicted_runs(42.5)
                .predicted_tier("Good")
                .tier_probabilities(Map.of("Poor", 0.1, "Average", 0.2, "Good", 0.5, "Excellent", 0.2))
                .model_name("SVR")
                .classifier_name("MLPClassifier")
                .build();

        when(restTemplate.postForObject(eq("http://localhost:5000/api/ml/predict"), eq(request), eq(PredictionResponseDto.class)))
                .thenReturn(flaskResponse);

        when(predictionRepository.save(any(Prediction.class))).thenAnswer(invocation -> {
            Prediction p = invocation.getArgument(0);
            p.setId(99L);
            return p;
        });

        PredictionResponseDto result = predictionService.predict(request);

        assertNotNull(result);
        assertEquals(42.5, result.getPredicted_runs());
        assertEquals("Good", result.getPredicted_tier());
        assertEquals(43, result.getPredictedRunsAsInt());
        assertEquals("SVR", result.getModel_name());
        assertEquals("MLPClassifier", result.getClassifier_name());
        assertEquals(99L, result.getId());
        verify(predictionRepository, times(1)).save(any(Prediction.class));
    }

    @Test
    @DisplayName("predict: Blank required fields (player, opponent, match_type) should throw IllegalArgumentException")
    void testPredictBlankFields() {
        assertThrows(IllegalArgumentException.class, () ->
                predictionService.predict(new PredictionRequestDto("", "MCG", "England", "ODI")));
        assertThrows(IllegalArgumentException.class, () ->
                predictionService.predict(new PredictionRequestDto("V Kohli", "MCG", "", "ODI")));
        assertThrows(IllegalArgumentException.class, () ->
                predictionService.predict(new PredictionRequestDto("V Kohli", "MCG", "England", "")));

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("predict: Optional venue (null or blank) should succeed and populate derived quartile tier")
    void testPredictWithoutVenueSucceeds() {
        PredictionRequestDto requestWithoutVenue = new PredictionRequestDto("V Kohli", "", "Australia", "T20I");

        PredictionResponseDto flaskResponse = PredictionResponseDto.builder()
                .player("V Kohli")
                .opponent("Australia")
                .venue(null)
                .match_type("t20i")
                .predicted_runs(23.5)
                .predicted_tier("Excellent")
                .tier_probabilities(Map.of("Poor", 0.003, "Average", 0.201, "Good", 0.368, "Excellent", 0.428))
                .model_name("SVR")
                .classifier_name("MLPClassifier")
                .build();

        when(restTemplate.postForObject(eq("http://localhost:5000/api/ml/predict"), any(), eq(PredictionResponseDto.class)))
                .thenReturn(flaskResponse);

        Prediction savedEntity = Prediction.builder()
                .id(101L)
                .playerName("V Kohli")
                .venue("Unspecified")
                .opponent("Australia")
                .matchType("t20i")
                .predictedRuns(23.5)
                .predictedTier("Excellent")
                .modelName("SVR")
                .classifierName("MLPClassifier")
                .predictionDate(LocalDateTime.now())
                .build();

        when(predictionRepository.save(any(Prediction.class))).thenReturn(savedEntity);

        PredictionResponseDto result = predictionService.predict(requestWithoutVenue);

        assertNotNull(result);
        assertEquals(23.5, result.getPredicted_runs());
        assertEquals("Good", result.getRegression_derived_tier()); // 23.5 falls in (9, 27] Good bracket
        assertEquals("Excellent", result.getPredicted_tier());
        assertEquals(101L, result.getId());
        verify(predictionRepository, times(1)).save(any(Prediction.class));
    }

    @Test
    @DisplayName("deriveTierFromRuns: Should match exact training dataset quartile brackets")
    void testDeriveTierFromRuns() {
        assertNull(PredictionService.deriveTierFromRuns(null));
        assertEquals("Poor", PredictionService.deriveTierFromRuns(0.0));
        assertEquals("Poor", PredictionService.deriveTierFromRuns(1.0));
        assertEquals("Average", PredictionService.deriveTierFromRuns(1.1));
        assertEquals("Average", PredictionService.deriveTierFromRuns(9.0));
        assertEquals("Good", PredictionService.deriveTierFromRuns(9.1));
        assertEquals("Good", PredictionService.deriveTierFromRuns(23.5));
        assertEquals("Good", PredictionService.deriveTierFromRuns(27.0));
        assertEquals("Excellent", PredictionService.deriveTierFromRuns(27.1));
        assertEquals("Excellent", PredictionService.deriveTierFromRuns(50.0));
    }

    @Test
    @DisplayName("predict: Flask 400 error should extract clean error message")
    void testPredictFlaskErrorExtractsMessage() {
        PredictionRequestDto request = new PredictionRequestDto("Unknown Player", "MCG", "England", "ODI");

        String errorJson = "{\"error\": \"No historical data found for 'Unknown Player'.\"}";
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                errorJson.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(restTemplate.postForObject(anyString(), any(), eq(PredictionResponseDto.class)))
                .thenThrow(ex);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                predictionService.predict(request));

        assertTrue(thrown.getMessage().contains("No historical data found for 'Unknown Player'"));
    }

    @Test
    @DisplayName("predict: Connection failure should throw IllegalStateException with helpful guidance")
    void testPredictFlaskUnavailable() {
        PredictionRequestDto request = new PredictionRequestDto("V Kohli", "MCG", "England", "ODI");

        when(restTemplate.postForObject(anyString(), any(), eq(PredictionResponseDto.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                predictionService.predict(request));

        assertTrue(thrown.getMessage().contains("AI Prediction service is currently unavailable"));
    }

    @Test
    @DisplayName("getPredictionHistory: Should return mapped history items for user")
    void testGetPredictionHistory() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Prediction p1 = Prediction.builder()
                .id(1L)
                .playerName("V Kohli")
                .opponent("Australia")
                .venue("Eden Gardens")
                .matchType("t20i")
                .predictedRuns(35.0)
                .predictedTier("Good")
                .modelName("SVR")
                .classifierName("MLPClassifier")
                .predictionDate(LocalDateTime.now())
                .requestedBy(testUser)
                .build();

        when(predictionRepository.findTop20ByRequestedByOrderByPredictionDateDesc(eq(testUser)))
                .thenReturn(List.of(p1));

        List<PredictionHistoryItemDto> history = predictionService.getPredictionHistory();

        assertEquals(1, history.size());
        assertEquals("V Kohli", history.get(0).getPlayer());
        assertEquals("Good", history.get(0).getPredictedTier());
        assertEquals(35.0, history.get(0).getPredictedRuns());
    }

    @Test
    @DisplayName("getPredictionMetadata: Should aggregate players, venues, opponents, and match formats")
    void testGetPredictionMetadata() {
        when(playerAnalyticsService.listAvailablePlayers()).thenReturn(List.of("AU Rashid", "V Kohli"));
        when(playerAnalyticsService.getAllVenues()).thenReturn(List.of("Eden Gardens", "MCG"));
        when(playerAnalyticsService.getAllOppositions()).thenReturn(List.of("Australia", "England", "India"));

        PredictionMetadataDto meta = predictionService.getPredictionMetadata();

        assertNotNull(meta);
        assertEquals(2, meta.getPlayers().size());
        assertEquals(2, meta.getVenues().size());
        assertEquals(3, meta.getOpponents().size());
        assertTrue(meta.getMatchTypes().contains("T20I"));
        assertTrue(meta.getMatchTypes().contains("ODI"));
        assertTrue(meta.getMatchTypes().contains("TEST"));
    }

    @Test
    @DisplayName("predict: Should throw IllegalStateException when FlaskProcessManager reports not ready")
    void testPredictThrowsWhenFlaskNotReady() {
        FlaskProcessManager mockManager = mock(FlaskProcessManager.class);
        when(mockManager.isReady()).thenReturn(false);

        PredictionService serviceWithManager = new PredictionService(
                restTemplate,
                "http://localhost:5000",
                predictionRepository,
                userRepository,
                playerAnalyticsService,
                mockManager
        );

        PredictionRequestDto request = new PredictionRequestDto("V Kohli", "Eden Gardens", "Australia", "T20I");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                serviceWithManager.predict(request));

        assertTrue(ex.getMessage().contains("Flask ML service is initializing or offline"));
        verifyNoInteractions(restTemplate);
    }
}
