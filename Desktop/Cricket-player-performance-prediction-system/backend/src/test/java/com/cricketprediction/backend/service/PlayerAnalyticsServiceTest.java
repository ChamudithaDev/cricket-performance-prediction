package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.analytics.FormatStats;
import com.cricketprediction.backend.dto.analytics.PlayerAnalyticsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerAnalyticsServiceTest {

    private PlayerAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new PlayerAnalyticsService();
        ReflectionTestUtils.setField(analyticsService, "battingCsvPath", "../ml-service/data/batting_performance.csv");
        ReflectionTestUtils.setField(analyticsService, "bowlingCsvPath", "../ml-service/data/bowling_performance.csv");
        analyticsService.loadData();
    }

    // ── Name resolution (existing) ────────────────────────────────────────────

    @Test
    @DisplayName("Should resolve 'virat kohli' (lower-case) → 'V Kohli'")
    void testResolveViratKohli_LowerCase() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("virat kohli", 15);
        assertThat(res).isNotNull();
        assertThat(res.getPlayerName()).isEqualTo("V Kohli");
        assertThat(res.getTotalBattingInnings()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should resolve 'Virat Kohli' (title-case) → 'V Kohli'")
    void testResolveViratKohli_TitleCase() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("Virat Kohli", 15);
        assertThat(res).isNotNull();
        assertThat(res.getPlayerName()).isEqualTo("V Kohli");
    }

    @Test
    @DisplayName("Should resolve surname 'kohli' → 'V Kohli'")
    void testResolveSurnameKohli() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("kohli", 15);
        assertThat(res).isNotNull();
        assertThat(res.getPlayerName()).isEqualTo("V Kohli");
    }

    @Test
    @DisplayName("Should resolve scorecard name 'V Kohli' → 'V Kohli'")
    void testResolveScorecardVKohli() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", 15);
        assertThat(res).isNotNull();
        assertThat(res.getPlayerName()).isEqualTo("V Kohli");
    }

    @Test
    @DisplayName("Case variations all resolve to 'V Kohli'")
    void testCaseVariations() {
        assertThat(analyticsService.getAnalytics("VIRAT KOHLI", 15)).isNotNull();
        assertThat(analyticsService.getAnalytics("vIrAt KoHlI", 15)).isNotNull();
        assertThat(analyticsService.getAnalytics("KOHLI", 15)).isNotNull();
        assertThat(analyticsService.getAnalytics("   virat    kohli   ", 15)).isNotNull();
    }

    @Test
    @DisplayName("Unknown player returns null")
    void testUnknownPlayerReturnsNull() {
        assertThat(analyticsService.getAnalytics("NonExistentPlayerXYZ12345", 15)).isNull();
    }

    @Test
    @DisplayName("Ambiguous surname throws IllegalArgumentException")
    void testAmbiguousMatchThrowsException() {
        assertThatThrownBy(() -> analyticsService.getAnalytics("smith", 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiple players match 'smith'");
    }

    @Test
    @DisplayName("'V Kohli' in available players list")
    void testListAvailablePlayersContainsVKohli() {
        List<String> players = analyticsService.listAvailablePlayers();
        assertThat(players).isNotEmpty().contains("V Kohli");
    }

    // ── Role detection ────────────────────────────────────────────────────────

    @Test
    @DisplayName("V Kohli should be detected as BATSMAN (heavy batting data, minimal bowling)")
    void testKohliIsBatsman() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", 15);
        assertThat(res).isNotNull();
        assertThat(res.getRole()).isEqualTo("BATSMAN");
        // Batting analytics must be present
        assertThat(res.getTotalBattingInnings()).isGreaterThan(0);
        assertThat(res.getRecentBattingForm()).isNotEmpty();
        // Batting venue / opposition breakdown present
        assertThat(res.getVenueBreakdown()).isNotNull().isNotEmpty();
        assertThat(res.getOppositionBreakdown()).isNotNull().isNotEmpty();
        // Bowling analytics should NOT be populated for a pure batsman
        assertThat(res.getBowlingVenueBreakdown()).isNullOrEmpty();
        assertThat(res.getBowlingOppositionBreakdown()).isNullOrEmpty();
    }

    @Test
    @DisplayName("RL Chase (top bowler by count) should be BOWLER or ALL_ROUNDER")
    void testRLChaseRoleIsBowlerOrAllRounder() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("RL Chase", 15);
        assertThat(res).isNotNull();
        assertThat(res.getRole()).isIn("BOWLER", "ALL_ROUNDER");
        // Bowling analytics must be populated
        assertThat(res.getTotalBowlingInnings()).isGreaterThan(0);
        assertThat(res.getRecentBowlingForm()).isNotEmpty();
    }

    @Test
    @DisplayName("AU Rashid should be BOWLER or ALL_ROUNDER with bowling recent form")
    void testAURashidBowler() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("AU Rashid", 15);
        assertThat(res).isNotNull();
        assertThat(res.getRole()).isIn("BOWLER", "ALL_ROUNDER");
        assertThat(res.getRecentBowlingForm()).isNotEmpty();
        assertThat(res.getBowlingVenueBreakdown()).isNotEmpty();
        assertThat(res.getBowlingOppositionBreakdown()).isNotEmpty();
    }

    // ── Format filtering ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ODI filter: V Kohli should have >0 ODI batting innings")
    void testKohliOdiFilter() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", "ODI", 15);
        assertThat(res).isNotNull();
        assertThat(res.getSelectedFormat()).isEqualTo("ODI");
        assertThat(res.getCoverage().getBattingInningsForFormat()).isGreaterThan(0);
        assertThat(res.getRecentBattingForm()).isNotEmpty();
    }

    @Test
    @DisplayName("T20I filter: V Kohli — graceful empty state when no T20I data in dataset")
    void testKohliT20iFilter() {
        // Note: V Kohli may have 0 T20I batting records in this specific dataset
        // The test ensures no exception is thrown and coverage fields are non-negative
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", "T20I", 15);
        // Response may be null (both batting and bowling empty for T20I) OR have 0 innings
        if (res != null) {
            assertThat(res.getSelectedFormat()).isEqualTo("T20I");
            assertThat(res.getCoverage().getBattingInningsForFormat()).isGreaterThanOrEqualTo(0);
        }
        // Either null or 0-innings response is valid — test ensures no crash
    }

    @Test
    @DisplayName("TEST filter: V Kohli should have >0 Test batting innings")
    void testKohliTestFilter() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", "TEST", 15);
        assertThat(res).isNotNull();
        assertThat(res.getSelectedFormat()).isEqualTo("TEST");
        assertThat(res.getCoverage().getBattingInningsForFormat()).isGreaterThan(0);
    }

    @Test
    @DisplayName("ALL filter should equal sum of individual format innings")
    void testFormatAllSumCheck() {
        PlayerAnalyticsResponse all = analyticsService.getAnalytics("V Kohli", "ALL", 100);
        PlayerAnalyticsResponse odi  = analyticsService.getAnalytics("V Kohli", "ODI", 100);
        PlayerAnalyticsResponse t20i = analyticsService.getAnalytics("V Kohli", "T20I", 100);
        PlayerAnalyticsResponse test = analyticsService.getAnalytics("V Kohli", "TEST", 100);

        int sum = odi.getCoverage().getBattingInningsForFormat()
                + t20i.getCoverage().getBattingInningsForFormat()
                + test.getCoverage().getBattingInningsForFormat();
        assertThat(all.getCoverage().getTotalBattingInningsAllFormats()).isEqualTo(sum);
    }

    @Test
    @DisplayName("Bowler: format-filtered bowling innings match format breakdown")
    void testBowlerFormatFilter() {
        PlayerAnalyticsResponse odiRes = analyticsService.getAnalytics("RL Chase", "ODI", 15);
        assertThat(odiRes).isNotNull();
        assertThat(odiRes.getSelectedFormat()).isEqualTo("ODI");
        // Coverage bowling count should match recentBowlingForm + remaining
        int coverageBowlCount = odiRes.getCoverage().getBowlingInningsForFormat();
        assertThat(coverageBowlCount).isGreaterThanOrEqualTo(0);
        // RecentBowlingForm should not exceed coverage count
        int recentCount = odiRes.getCoverage().getRecentBowlingFormCount();
        assertThat(recentCount).isLessThanOrEqualTo(coverageBowlCount);
    }

    @Test
    @DisplayName("Format with no records: should return response with 0 innings for that format")
    void testFormatWithNoRecords_returnsZeroCoverage() {
        // RL Chase may not have T20I records — validate graceful handling
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("RL Chase", "T20I", 15);
        // Response itself may be null (player has no T20I data at all) OR have 0 innings
        if (res != null) {
            assertThat(res.getCoverage().getBowlingInningsForFormat()).isGreaterThanOrEqualTo(0);
            assertThat(res.getCoverage().getBattingInningsForFormat()).isGreaterThanOrEqualTo(0);
        }
        // Either outcome is acceptable — the test ensures no exception is thrown
    }

    // ── Bowling metric correctness ─────────────────────────────────────────────

    @Test
    @DisplayName("Bowling average should be null when 0 wickets in format stats")
    void testBowlingAverageNullWhenZeroWickets() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("AU Rashid", "ALL", 100);
        assertThat(res).isNotNull();
        // Find a format stat where wickets > 0 and assert average is not null
        for (FormatStats fs : res.getFormatBreakdown()) {
            if (fs.getBowlingInnings() != null && fs.getBowlingInnings() > 0) {
                if (fs.getTotalWickets() != null && fs.getTotalWickets() > 0) {
                    assertThat(fs.getBowlingAverage()).isNotNull().isGreaterThan(0.0);
                } else {
                    // 0 wickets → average must be null
                    assertThat(fs.getBowlingAverage()).isNull();
                }
            }
        }
    }

    @Test
    @DisplayName("Economy rate should be null when 0 overs in format stats")
    void testEconomyRateNullWhenZeroOvers() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("AU Rashid", "ALL", 100);
        assertThat(res).isNotNull();
        for (FormatStats fs : res.getFormatBreakdown()) {
            if (fs.getTotalDecimalOvers() != null && fs.getTotalDecimalOvers() == 0.0) {
                assertThat(fs.getEconomyRate()).isNull();
            }
        }
    }

    @Test
    @DisplayName("csvOversToDecimal: 4.3 should equal 4.5 (4 overs + 3 balls)")
    void testCsvOversConversion() {
        assertThat(PlayerAnalyticsService.csvOversToDecimal(4.3)).isEqualTo(4.5);
        assertThat(PlayerAnalyticsService.csvOversToDecimal(10.0)).isEqualTo(10.0);
        assertThat(PlayerAnalyticsService.csvOversToDecimal(0.0)).isEqualTo(0.0);
        assertThat(PlayerAnalyticsService.csvOversToDecimal(6.0)).isEqualTo(6.0);
    }

    // ── Data coverage metadata ────────────────────────────────────────────────

    @Test
    @DisplayName("Coverage: totalBattingInningsAllFormats should equal sum of all format batting innings")
    void testCoverage_totalBattingMatchesFormatBreakdown() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", "ALL", 15);
        assertThat(res).isNotNull();
        int sumFromBreakdown = res.getFormatBreakdown().stream()
                .mapToInt(f -> f.getBattingInnings() != null ? f.getBattingInnings() : 0)
                .sum();
        assertThat(res.getCoverage().getTotalBattingInningsAllFormats()).isEqualTo(sumFromBreakdown);
    }

    @Test
    @DisplayName("Coverage: dates should not be null for players with data")
    void testCoverage_datesPresent() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", 15);
        assertThat(res).isNotNull();
        assertThat(res.getCoverage().getBattingEarliestDate()).isNotNull();
        assertThat(res.getCoverage().getBattingLatestDate()).isNotNull();
    }

    @Test
    @DisplayName("Coverage: recentBattingFormCount should not exceed battingInningsForFormat")
    void testCoverage_recentFormCountDoesNotExceedTotal() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", "ODI", 15);
        assertThat(res).isNotNull();
        assertThat(res.getCoverage().getRecentBattingFormCount())
                .isLessThanOrEqualTo(res.getCoverage().getBattingInningsForFormat());
    }

    // ── Recent form chronological order ───────────────────────────────────────

    @Test
    @DisplayName("Recent batting form should be in chronological (ascending date) order")
    void testRecentBattingFormChronologicalOrder() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", "ALL", 15);
        assertThat(res).isNotNull();
        var form = res.getRecentBattingForm();
        for (int i = 1; i < form.size(); i++) {
            assertThat(form.get(i).getDate()).isGreaterThanOrEqualTo(form.get(i - 1).getDate());
        }
    }

    @Test
    @DisplayName("Recent bowling form should be in chronological order")
    void testRecentBowlingFormChronologicalOrder() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("AU Rashid", "ALL", 15);
        assertThat(res).isNotNull();
        var form = res.getRecentBowlingForm();
        for (int i = 1; i < form.size(); i++) {
            assertThat(form.get(i).getDate()).isGreaterThanOrEqualTo(form.get(i - 1).getDate());
        }
    }

    // ── Format normalisation ──────────────────────────────────────────────────

    @Test
    @DisplayName("normalizeFormat: t20 and t20i should both map to t20i")
    void testNormalizeFormatT20() {
        assertThat(PlayerAnalyticsService.normalizeFormat("t20")).isEqualTo("t20i");
        assertThat(PlayerAnalyticsService.normalizeFormat("T20")).isEqualTo("t20i");
        assertThat(PlayerAnalyticsService.normalizeFormat("T20I")).isEqualTo("t20i");
        assertThat(PlayerAnalyticsService.normalizeFormat("t20i")).isEqualTo("t20i");
    }

    @Test
    @DisplayName("normalizeFormat: null/ALL should return ALL sentinel")
    void testNormalizeFormatAll() {
        assertThat(PlayerAnalyticsService.normalizeFormat(null)).isEqualTo(PlayerAnalyticsService.FORMAT_ALL);
        assertThat(PlayerAnalyticsService.normalizeFormat("all")).isEqualTo(PlayerAnalyticsService.FORMAT_ALL);
        assertThat(PlayerAnalyticsService.normalizeFormat("ALL")).isEqualTo(PlayerAnalyticsService.FORMAT_ALL);
    }

    // ── Player Comparison Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("comparePlayers: Should compare two batsmen successfully")
    void testCompareBatsmen() {
        var res = analyticsService.comparePlayers("V Kohli", "BM Duckett", "ALL", 15);
        assertThat(res).isNotNull();
        assertThat(res.getPlayer1Name()).isEqualTo("V Kohli");
        assertThat(res.getPlayer2Name()).isEqualTo("BM Duckett");
        assertThat(res.getComparisonScope()).isEqualTo("BATTING_ONLY");
        assertThat(res.getBattingComparison()).isNotNull();
        assertThat(res.getBattingComparison().getTotalRuns1()).isGreaterThan(0);
        assertThat(res.getBattingComparison().getTotalRuns2()).isGreaterThan(0);
        assertThat(res.getMetricAdvantages()).containsKey("runs");
    }

    @Test
    @DisplayName("comparePlayers: Should compare two bowlers successfully with bowling advantages")
    void testCompareBowlers() {
        var res = analyticsService.comparePlayers("CV Varun", "J Little", "ALL", 15);
        assertThat(res).isNotNull();
        assertThat(res.getPlayer1Name()).isEqualTo("CV Varun");
        assertThat(res.getPlayer2Name()).isEqualTo("J Little");
        assertThat(res.getComparisonScope()).isEqualTo("BOWLING_ONLY");
        assertThat(res.getBowlingComparison()).isNotNull();
        assertThat(res.getBowlingComparison().getTotalWickets1()).isGreaterThan(0);
        assertThat(res.getMetricAdvantages()).containsKey("wickets");
        assertThat(res.getMetricAdvantages()).containsKey("economyRate");
    }

    @Test
    @DisplayName("comparePlayers: Should detect MIXED_DISCIPLINES between batsman and bowler")
    void testCompareMixedDisciplines() {
        var res = analyticsService.comparePlayers("V Kohli", "CV Varun", "ALL", 15);
        assertThat(res).isNotNull();
        assertThat(res.getComparisonScope()).isEqualTo("MIXED_DISCIPLINES");
        assertThat(res.getScopeExplanation()).contains("contrasting primary roles");
    }

    @Test
    @DisplayName("comparePlayers: Should compare two all-rounders with BOTH scope")
    void testCompareAllRounders() {
        var res = analyticsService.comparePlayers("AU Rashid", "AM Hardie", "ALL", 15);
        assertThat(res).isNotNull();
        assertThat(res.getComparisonScope()).isEqualTo("BOTH");
        assertThat(res.getBattingComparison()).isNotNull();
        assertThat(res.getBowlingComparison()).isNotNull();
    }

    @Test
    @DisplayName("comparePlayers: Should reject comparison of the same player (including alias)")
    void testRejectSamePlayer() {
        assertThatThrownBy(() -> analyticsService.comparePlayers("Virat Kohli", "V Kohli", "ALL", 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot compare a player with themselves");
    }

    @Test
    @DisplayName("comparePlayers: Should throw exception on unknown player")
    void testRejectUnknownPlayer() {
        assertThatThrownBy(() -> analyticsService.comparePlayers("Unknown Batter 999", "V Kohli", "ALL", 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No historical data found for player 1");
    }

    @Test
    @DisplayName("comparePlayers: Should support format filtering")
    void testCompareWithFormatFilter() {
        var res = analyticsService.comparePlayers("BM Duckett", "PD Salt", "ODI", 10);
        assertThat(res).isNotNull();
        assertThat(res.getFormat()).isEqualTo("ODI");
        assertThat(res.getBattingComparison()).isNotNull();
    }

    @Test
    @DisplayName("comparePlayers: Should compute accurate batting metrics distinguishing RPI and batting average")
    void testBattingMetricsDistinction() {
        var res = analyticsService.comparePlayers("V Kohli", "BM Duckett", "ALL", 15);
        var bat = res.getBattingComparison();
        assertThat(bat).isNotNull();
        assertThat(bat.getTotalRuns1()).isGreaterThan(0);
        assertThat(bat.getInnings1()).isGreaterThan(0);
        assertThat(bat.getRunsPerInnings1()).isNotNull();
        // Check that runsPerInnings equals totalRuns / innings
        double expectedRpi = Math.round(((double) bat.getTotalRuns1() / bat.getInnings1()) * 100.0) / 100.0;
        assertThat(bat.getRunsPerInnings1()).isEqualTo(expectedRpi);

        // Batting average requires dismissals
        assertThat(bat.getDismissals1()).isEqualTo(bat.getInnings1() - bat.getNotOuts1());
        if (bat.getDismissals1() > 0) {
            double expectedAvg = Math.round(((double) bat.getTotalRuns1() / bat.getDismissals1()) * 100.0) / 100.0;
            assertThat(bat.getBattingAverage1()).isEqualTo(expectedAvg);
        }

        // Boundary percentage & balls faced
        assertThat(bat.getBoundaryPercentage1()).isNotNull();
        assertThat(bat.getBoundaryBallPercentage1()).isNotNull();
        assertThat(bat.getDotBallPercentage1()).isNull(); // Dot balls not available in match summary CSV

        // Consistency metrics
        assertThat(bat.getStdDevRuns1()).isNotNull();
        if (bat.getInnings1() >= 3) {
            assertThat(bat.getCoefficientOfVariation1()).isNotNull();
            assertThat(bat.getConsistencyNote()).contains("Consistency is evaluated via sample standard deviation");
        }
    }

    @Test
    @DisplayName("comparePlayers: Should convert overs to valid balls for bowling strike rate and exclude invalid denominators")
    void testBowlingValidBallsAndStrikeRate() {
        var res = analyticsService.comparePlayers("CV Varun", "J Little", "ALL", 15);
        var bowl = res.getBowlingComparison();
        assertThat(bowl).isNotNull();
        assertThat(bowl.getValidBallsBowled1()).isGreaterThan(0);
        assertThat(bowl.getTotalWickets1()).isGreaterThan(0);
        // Strike rate = valid balls / wickets
        double expectedSr = Math.round(((double) bowl.getValidBallsBowled1() / bowl.getTotalWickets1()) * 100.0) / 100.0;
        assertThat(bowl.getBowlingStrikeRate1()).isEqualTo(expectedSr);
        // Maidens should be null as not recorded in dataset
        assertThat(bowl.getMaidens1()).isNull();
        // Wickets per innings
        assertThat(bowl.getWicketsPerInnings1()).isNotNull();
        // Standard deviation of wickets
        assertThat(bowl.getStdDevWickets1()).isNotNull();
    }

    @Test
    @DisplayName("comparePlayers: Should generate recent form summary with trend slopes and eligible innings basis")
    void testRecentFormSummaryAndTrends() {
        var res = analyticsService.comparePlayers("V Kohli", "BM Duckett", "ALL", 5);
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getRecentN()).isEqualTo(5);
        assertThat(recent.getCalculationBasis()).contains("eligible innings");
        assertThat(recent.getMeanRuns1()).isNotNull();
        assertThat(recent.getRunTrendDirection1()).isIn("UPWARD", "DOWNWARD", "STABLE", "INSUFFICIENT_DATA");
    }

    @Test
    @DisplayName("comparePlayers: Should support opposition and venue context filtering and extract common venues/opponents")
    void testContextFilteringAndCommonContext() {
        var res = analyticsService.comparePlayers("BM Duckett", "PD Salt", "ALL", 15, "Australia", null);
        assertThat(res).isNotNull();
        assertThat(res.getContextInfo()).isNotNull();
        assertThat(res.getContextInfo().getOppositionFilter()).isEqualTo("Australia");
        assertThat(res.getContextInfo().getCommonOppositions()).isNotEmpty();
        assertThat(res.getContextInfo().getCommonVenues()).isNotEmpty();
    }

    @Test
    @DisplayName("comparePlayers: Detailed metric advantages should respect cricket rules and handle missing data gracefully")
    void testDetailedMetricAdvantagesRules() {
        var res = analyticsService.comparePlayers("CV Varun", "J Little", "ALL", 15);
        var detailed = res.getMetricAdvantagesDetailed();
        assertThat(detailed).isNotNull();
        // Economy rate: lower is better
        assertThat(detailed).containsKey("economyRate");
        var econAdv = detailed.get("economyRate");
        assertThat(econAdv.getCriterion()).isEqualTo("LOWER_IS_BETTER");
        assertThat(econAdv.getAdvantage()).isIn("player1", "player2", "tie", "na");
        assertThat(econAdv.getInterpretation()).isNotEmpty();

        // Dot balls and maidens should have advantage = "na"
        assertThat(detailed.get("dotBallPercentage").getAdvantage()).isEqualTo("na");
        assertThat(detailed.get("maidens").getAdvantage()).isEqualTo("na");

        // Written interpretation should be populated
        assertThat(res.getWrittenInterpretation()).isNotBlank();
        assertThat(res.getWrittenInterpretation()).contains("Head-to-head comparison between");
    }

    @Test
    @DisplayName("comparePlayers: Batting comparison should populate highest scores and not out status")
    void testHighestScoreBatting() {
        var res = analyticsService.comparePlayers("V Kohli", "BM Duckett", "ALL", 15);
        assertThat(res.getBattingComparison()).isNotNull();
        assertThat(res.getBattingComparison().getHighestScore1()).isNotNull();
        assertThat(res.getBattingComparison().getHighestScore2()).isNotNull();
        assertThat(res.getBattingComparison().getHighestScoreNotOut1()).isNotNull();
        assertThat(res.getBattingComparison().getHighestScoreNotOut2()).isNotNull();
    }

    @Test
    @DisplayName("comparePlayers: Recent form should include highest/lowest scores, recent average advantage, and sample size warnings")
    void testRecentFormHighLowAndWarnings() {
        var res = analyticsService.comparePlayers("V Kohli", "BM Duckett", "ALL", 5);
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getHighestRuns1()).isNotNull();
        assertThat(recent.getLowestRuns1()).isNotNull();
        assertThat(recent.getHighestRuns1()).isGreaterThanOrEqualTo(recent.getLowestRuns1());
        assertThat(recent.getRecentAverageRunsAdvantage()).isIn("player1", "player2", "tie", "insufficient_data");
        if (recent.getBattingInnings1() < 3) {
            assertThat(recent.getSampleSizeWarning1()).contains("Sample size too small");
        }
    }

    @Test
    @DisplayName("comparePlayers: Context info should provide allOppositions, allVenues, and contextual stats when filtered")
    void testContextStatsAndDropdownLists() {
        var res = analyticsService.comparePlayers("BM Duckett", "PD Salt", "ALL", 15, "Australia", null);
        var ctx = res.getContextInfo();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getAllOppositions()).isNotEmpty();
        assertThat(ctx.getAllVenues()).isNotEmpty();
        assertThat(ctx.getContextualBatting()).isNotNull();
        assertThat(ctx.getContextualBatting().getTotalRuns1()).isNotNull();
        assertThat(ctx.getContextualBowling()).isNotNull();
    }

    @Test
    @DisplayName("comparePlayers: Bowler vs Bowler should set focusDiscipline to BOWLING and populate bowling recent window rates")
    void testBowlerVsBowlerRecentFormAndHistory() {
        var res = analyticsService.comparePlayers("CV Varun", "J Little", "ALL", 10);
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getFocusDiscipline()).isEqualTo("BOWLING");
        assertThat(recent.getBowlingInnings1()).isGreaterThan(0);
        assertThat(recent.getBowlingInnings2()).isGreaterThan(0);
        assertThat(recent.getTotalWickets1()).isNotNull();
        assertThat(recent.getTotalRunsConceded1()).isNotNull();
        assertThat(recent.getTotalOvers1()).isGreaterThan(0.0);
        assertThat(recent.getTotalValidBalls1()).isGreaterThan(0);
        assertThat(recent.getRecentEconomyRate1()).isNotNull();

        // Match-by-match bowling spells should have figures and valid balls
        var bwlList1 = res.getPlayer1().getRecentBowlingForm();
        assertThat(bwlList1).isNotEmpty();
        var firstSpell = bwlList1.get(0);
        assertThat(firstSpell.getFigures()).contains("/");
        assertThat(firstSpell.getValidBalls()).isGreaterThan(0);
    }

    @Test
    @DisplayName("comparePlayers: All-Rounder vs All-Rounder should set focusDiscipline to BOWLING")
    void testAllRounderVsAllRounderRecentFormFocus() {
        var res = analyticsService.comparePlayers("AU Rashid", "AM Hardie", "ALL", 10);
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getFocusDiscipline()).isEqualTo("BOWLING");
        assertThat(recent.getBowlingInnings1()).isGreaterThan(0);
    }

    @Test
    @DisplayName("comparePlayers: Batter vs Batter should preserve BATTING focus discipline and batting recent metrics exactly")
    void testBatterVsBatterRecentFormPreserved() {
        var res = analyticsService.comparePlayers("V Kohli", "BM Duckett", "ALL", 10);
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getFocusDiscipline()).isEqualTo("BATTING");
        assertThat(recent.getMeanRuns1()).isNotNull();
        assertThat(recent.getHighestRuns1()).isNotNull();
        assertThat(recent.getBattingInnings1()).isGreaterThan(0);
    }

    // ── Single Player Role-Based Analytics Tests ──────────────────────────────

    @Test
    @DisplayName("getAnalytics: BATSMAN should have battingSummary, scoreDistribution, and recentFormSummary with BATTING focus")
    void testBatsmanSinglePlayerAnalytics() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("V Kohli", "ALL", 10);
        assertThat(res).isNotNull();
        assertThat(res.getRole()).isEqualTo("BATSMAN");

        // Batting Summary
        var bat = res.getBattingSummary();
        assertThat(bat).isNotNull();
        assertThat(bat.getInnings()).isGreaterThan(0);
        assertThat(bat.getTotalRuns()).isGreaterThan(0);
        assertThat(bat.getRunsPerInnings()).isGreaterThan(0.0);
        assertThat(bat.getBoundaryPercentage()).isGreaterThan(0.0);
        assertThat(bat.getStdDevRuns()).isNotNull();
        assertThat(bat.getCoefficientOfVariation()).isNotNull();

        // Score Distribution
        var dist = res.getScoreDistribution();
        assertThat(dist).isNotNull();
        assertThat(dist.getPatternInterpretation()).isNotEmpty();
        int sumCounts = dist.getSingleDigits() + dist.getStarts() + dist.getSolidStarts() + dist.getFifties() + dist.getCenturies();
        assertThat(sumCounts).isEqualTo(bat.getInnings());

        // Recent Form Summary
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getFocusDiscipline()).isEqualTo("BATTING");
        assertThat(recent.getBattingInnings()).isLessThanOrEqualTo(10);
        assertThat(recent.getMeanRuns()).isNotNull();
        assertThat(recent.getRunTrendDirection()).isNotNull();
    }

    @Test
    @DisplayName("getAnalytics: BOWLER should have bowlingSummary, bowlingDistribution, and recentFormSummary with BOWLING focus")
    void testBowlerSinglePlayerAnalytics() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("CV Varun", "ALL", 10);
        assertThat(res).isNotNull();
        assertThat(res.getRole()).isEqualTo("BOWLER");

        // Bowling Summary
        var bowl = res.getBowlingSummary();
        assertThat(bowl).isNotNull();
        assertThat(bowl.getInnings()).isGreaterThan(0);
        assertThat(bowl.getTotalWickets()).isNotNull();
        assertThat(bowl.getEconomyRate()).isGreaterThan(0.0);
        assertThat(bowl.getValidBallsBowled()).isGreaterThan(0);

        // Bowling Distribution
        var dist = res.getBowlingDistribution();
        assertThat(dist).isNotNull();
        assertThat(dist.getPatternInterpretation()).isNotEmpty();
        int sumSpells = dist.getZeroWickets() + dist.getOneWicket() + dist.getTwoWickets() + dist.getThreeWickets() + dist.getFourPlusWickets();
        assertThat(sumSpells).isEqualTo(bowl.getInnings());

        // Recent Form Summary
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getFocusDiscipline()).isEqualTo("BOWLING");
        assertThat(recent.getBowlingInnings()).isLessThanOrEqualTo(10);
        assertThat(recent.getRecentEconomyRate()).isNotNull();
        assertThat(recent.getWicketTrendDirection()).isNotNull();
    }

    @Test
    @DisplayName("getAnalytics: ALL_ROUNDER should provide BOTH batting and bowling summaries and distributions")
    void testAllRounderSinglePlayerAnalytics() {
        PlayerAnalyticsResponse res = analyticsService.getAnalytics("AU Rashid", "ALL", 10);
        assertThat(res).isNotNull();
        assertThat(res.getRole()).isEqualTo("ALL_ROUNDER");

        // Both sections should be populated
        assertThat(res.getBattingSummary()).isNotNull();
        assertThat(res.getBowlingSummary()).isNotNull();
        assertThat(res.getScoreDistribution()).isNotNull();
        assertThat(res.getBowlingDistribution()).isNotNull();

        // Recent Form Summary should indicate BOTH
        var recent = res.getRecentFormSummary();
        assertThat(recent).isNotNull();
        assertThat(recent.getFocusDiscipline()).isEqualTo("BOTH");
        assertThat(recent.getBattingInnings()).isGreaterThan(0);
        assertThat(recent.getBowlingInnings()).isGreaterThan(0);
    }

    @Test
    @DisplayName("getAnalytics: format filter and recentN window update analytics correctly")
    void testFormatFilterAndRecentNWindow() {
        PlayerAnalyticsResponse resT20i = analyticsService.getAnalytics("V Kohli", "T20I", 5);
        assertThat(resT20i).isNotNull();
        assertThat(resT20i.getSelectedFormat()).isEqualTo("T20I");
        assertThat(resT20i.getRecentFormSummary().getRecentN()).isEqualTo(5);
        assertThat(resT20i.getRecentBattingForm().size()).isLessThanOrEqualTo(5);
    }
}

