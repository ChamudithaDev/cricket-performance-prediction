package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.analytics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerStrengthsServiceTest {

    private PlayerAnalyticsService analyticsService;
    private PlayerStrengthsService strengthsService;

    @BeforeEach
    void setUp() {
        analyticsService = new PlayerAnalyticsService();
        ReflectionTestUtils.setField(analyticsService, "battingCsvPath", "../ml-service/data/batting_performance.csv");
        ReflectionTestUtils.setField(analyticsService, "bowlingCsvPath", "../ml-service/data/bowling_performance.csv");
        analyticsService.loadData();

        strengthsService = new PlayerStrengthsService(analyticsService);
        ReflectionTestUtils.setField(strengthsService, "batterMatchupsCsvPath", "../ml-service/data/player_bowler_matchups.csv");
        ReflectionTestUtils.setField(strengthsService, "bowlerMatchupsCsvPath", "../ml-service/data/bowler_batter_matchups.csv");
        ReflectionTestUtils.setField(strengthsService, "phaseCsvPath", "../ml-service/data/player_phase_performance.csv");
        strengthsService.loadData();
    }

    @Test
    @DisplayName("Should return valid batter matchups for V Kohli in ODI")
    void testVKohliOdiBatterMatchups() {
        PlayerStrengthsResponse res = strengthsService.getPlayerStrengths("V Kohli", "ODI");

        assertThat(res).isNotNull();
        assertThat(res.getPlayerName()).isEqualTo("V Kohli");
        assertThat(res.getSelectedFormat()).isEqualTo("ODI");
        assertThat(res.isHasBattingMatchups()).isTrue();
        assertThat(res.getBatterMatchups()).isNotEmpty();

        // Check first matchup sorted by balls faced
        BatterVsBowlerDto topMatchup = res.getBatterMatchups().get(0);
        assertThat(topMatchup.getBowler()).isNotBlank();
        assertThat(topMatchup.getBallsFaced()).isGreaterThan(0);
        assertThat(topMatchup.getRuns()).isGreaterThanOrEqualTo(0);
        assertThat(topMatchup.getStrikeRate()).isGreaterThanOrEqualTo(0.0);
        assertThat(topMatchup.getDotPct()).isBetween(0.0, 100.0);
        assertThat(topMatchup.getBoundaryPct()).isBetween(0.0, 100.0);

        // Sample size badges check
        for (BatterVsBowlerDto m : res.getBatterMatchups()) {
            if (m.getBallsFaced() < 20) {
                assertThat(m.getSampleSizeBadge()).isEqualTo("LIMITED");
                assertThat(m.getAssessment()).isEqualTo("LIMITED_DATA");
            } else {
                assertThat(m.getSampleSizeBadge()).isEqualTo("SUFFICIENT");
                assertThat(m.getAssessment()).isNotEqualTo("LIMITED_DATA");
            }
        }
    }

    @Test
    @DisplayName("Should return valid bowler matchups for JJ Bumrah in T20I")
    void testJJBumrahT20IBowlerMatchups() {
        PlayerStrengthsResponse res = strengthsService.getPlayerStrengths("JJ Bumrah", "T20I");

        assertThat(res).isNotNull();
        assertThat(res.getPlayerName()).isEqualTo("JJ Bumrah");
        assertThat(res.getSelectedFormat()).isEqualTo("T20I");
        assertThat(res.isHasBowlingMatchups()).isTrue();
        assertThat(res.getBowlerMatchups()).isNotEmpty();

        BowlerVsBatterDto topMatchup = res.getBowlerMatchups().get(0);
        assertThat(topMatchup.getBatter()).isNotBlank();
        assertThat(topMatchup.getLegalBalls()).isGreaterThan(0);
        assertThat(topMatchup.getEconomy()).isGreaterThanOrEqualTo(0.0);
        assertThat(topMatchup.getDotPct()).isBetween(0.0, 100.0);

        for (BowlerVsBatterDto m : res.getBowlerMatchups()) {
            if (m.getLegalBalls() < 20) {
                assertThat(m.getSampleSizeBadge()).isEqualTo("LIMITED");
                assertThat(m.getAssessment()).isEqualTo("LIMITED_DATA");
            } else {
                assertThat(m.getSampleSizeBadge()).isEqualTo("SUFFICIENT");
                assertThat(m.getAssessment()).isNotEqualTo("LIMITED_DATA");
            }
        }
    }

    @Test
    @DisplayName("Should return Powerplay, Middle, and Death phase breakdowns for ODI/T20I")
    void testPhasePerformanceLimitedOvers() {
        PlayerStrengthsResponse res = strengthsService.getPlayerStrengths("V Kohli", "ODI");

        assertThat(res.isHasPhaseData()).isTrue();
        assertThat(res.getBattingPhases()).isNotEmpty();

        List<String> phases = res.getBattingPhases().stream().map(PhasePerformanceDto::getPhase).toList();
        assertThat(phases).containsAnyOf("POWERPLAY", "MIDDLE", "DEATH");

        // Verify phase fields are properly computed from totals
        for (PhasePerformanceDto phaseDto : res.getBattingPhases()) {
            assertThat(phaseDto.getBalls()).isGreaterThan(0);
            assertThat(phaseDto.getRateStat()).isGreaterThanOrEqualTo(0.0);
            if (phaseDto.getBalls() < 30) {
                assertThat(phaseDto.getSampleSizeBadge()).isEqualTo("LIMITED");
            } else {
                assertThat(phaseDto.getSampleSizeBadge()).isEqualTo("SUFFICIENT");
            }
        }
    }

    @Test
    @DisplayName("Should handle Test format phase unavailable gracefully with notice")
    void testTestFormatPhaseUnavailable() {
        PlayerStrengthsResponse res = strengthsService.getPlayerStrengths("V Kohli", "TEST");

        assertThat(res.getSelectedFormat()).isEqualTo("TEST");
        assertThat(res.getBattingPhases()).isEmpty();
        assertThat(res.getBowlingPhases()).isEmpty();
        assertThat(res.getPhaseNotice()).isNotBlank();
        assertThat(res.getPhaseNotice()).contains("limited-overs cricket");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when player is unknown")
    void testUnknownPlayerThrowsException() {
        assertThatThrownBy(() -> strengthsService.getPlayerStrengths("Totally NonExistent Player", "ALL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No player found");
    }

    @Test
    @DisplayName("Should default blank format to ALL and aggregate records")
    void testBlankFormatDefaultsToAll() {
        PlayerStrengthsResponse res = strengthsService.getPlayerStrengths("V Kohli", "");
        assertThat(res.getSelectedFormat()).isEqualTo("ALL");
        assertThat(res.getBatterMatchups()).isNotEmpty();
    }
}
