package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.toss.TossDecisionResponse;
import com.cricketprediction.backend.dto.toss.TossRecommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossDecisionServiceTest {

    private TossDecisionService service;

    @BeforeEach
    void setUp() {
        service = new TossDecisionService();
        service.init();
    }

    // ─── 1. Real Dataset Verification ──────────────────────────────────────────

    @Test
    @DisplayName("Should successfully load actual cricsheet_matches.csv with 6939 rows and correct reference date")
    void testActualDatasetLoading() {
        assertThat(service.getLatestReliableDate()).isEqualTo("2026-07-30");
        assertThat(service.getDuplicatesSkipped()).isEqualTo(0);

        List<String> odiVenues = service.getAvailableVenues("ODI");
        assertThat(odiVenues).isNotEmpty();
        assertThat(odiVenues).contains("R Premadasa Stadium, Colombo");

        List<String> t20iVenues = service.getAvailableVenues("T20I");
        assertThat(t20iVenues).isNotEmpty();

        List<String> testVenues = service.getAvailableVenues("TEST");
        assertThat(testVenues).isNotEmpty();
    }

    @Test
    @DisplayName("Real venue test: R Premadasa Stadium, Colombo in ODI should recommend BAT_FIRST")
    void testRealVenuePremadasaBatFirst() {
        TossDecisionResponse res = service.analyzeTossDecision("R Premadasa Stadium, Colombo", "ODI");

        assertThat(res).isNotNull();
        assertThat(res.getVenue()).isEqualTo("R Premadasa Stadium, Colombo");
        assertThat(res.getFormat()).isEqualTo("ODI");
        assertThat(res.getReferenceDate()).isEqualTo("2026-07-30");
        assertThat(res.getWindowStartDate()).isEqualTo("2024-07-30");
        assertThat(res.getWindowEndDate()).isEqualTo("2026-07-30");

        assertThat(res.getTotalEligibleMatches()).isEqualTo(10);
        assertThat(res.getDecisiveMatches()).isEqualTo(9);
        assertThat(res.getBatFirstWins()).isEqualTo(8);
        assertThat(res.getChaseWins()).isEqualTo(1);
        assertThat(res.getBatFirstWinRate()).isEqualTo(88.9);
        assertThat(res.getChaseWinRate()).isEqualTo(11.1);
        assertThat(res.getPercentagePointDifference()).isEqualTo(77.8);
        assertThat(res.getRecommendation()).isEqualTo(TossRecommendation.BAT_FIRST);
        assertThat(res.getRecommendationLabel()).isEqualTo("Bat First");

        // Exactly 1 non-decisive match (Sri Lanka vs India tied match 1442990 on 2024-08-02)
        assertThat(res.getExcludedBreakdown().getTotalExcludedCount()).isEqualTo(1);
        assertThat(res.getExcludedBreakdown().getTieCount()).isEqualTo(1);
        assertThat(res.getExplanation()).contains("88.9%");
        assertThat(res.getExplanation()).contains("prior conditions and do not guarantee future match outcomes");
    }

    @Test
    @DisplayName("Real venue test: TCA Oval, Blantyre in T20I should recommend FIELD_FIRST")
    void testRealVenueTcaOvalFieldFirst() {
        TossDecisionResponse res = service.analyzeTossDecision("TCA Oval, Blantyre", "T20I");

        assertThat(res).isNotNull();
        assertThat(res.getDecisiveMatches()).isEqualTo(14);
        assertThat(res.getBatFirstWins()).isEqualTo(3);
        assertThat(res.getChaseWins()).isEqualTo(11);
        assertThat(res.getBatFirstWinRate()).isEqualTo(21.4);
        assertThat(res.getChaseWinRate()).isEqualTo(78.6);
        assertThat(res.getPercentagePointDifference()).isEqualTo(-57.2);
        assertThat(res.getRecommendation()).isEqualTo(TossRecommendation.FIELD_FIRST);
    }

    @Test
    @DisplayName("Real venue test: Terdthai Cricket Ground, Bangkok in T20I should recommend NO_CLEAR_ADVANTAGE")
    void testRealVenueNeutralAdvantage() {
        TossDecisionResponse res = service.analyzeTossDecision("Terdthai Cricket Ground, Bangkok", "T20I");

        assertThat(res).isNotNull();
        assertThat(res.getDecisiveMatches()).isEqualTo(18);
        assertThat(res.getBatFirstWins()).isEqualTo(9);
        assertThat(res.getChaseWins()).isEqualTo(9);
        assertThat(res.getBatFirstWinRate()).isEqualTo(50.0);
        assertThat(res.getChaseWinRate()).isEqualTo(50.0);
        assertThat(res.getPercentagePointDifference()).isEqualTo(0.0);
        assertThat(res.getRecommendation()).isEqualTo(TossRecommendation.NO_CLEAR_ADVANTAGE);
    }

    // ─── 2. Filtering & Normalization ──────────────────────────────────────────

    @Test
    @DisplayName("Should handle venue and format case-insensitively with whitespace trimming")
    void testVenueAndFormatCaseInsensitivity() {
        TossDecisionResponse res1 = service.analyzeTossDecision("  r premadasa stadium, colombo  ", "odi");
        TossDecisionResponse res2 = service.analyzeTossDecision("R Premadasa Stadium, Colombo", "ODI");

        assertThat(res1.getDecisiveMatches()).isEqualTo(res2.getDecisiveMatches());
        assertThat(res1.getBatFirstWins()).isEqualTo(res2.getBatFirstWins());
        assertThat(res1.getRecommendation()).isEqualTo(res2.getRecommendation());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on blank venue or unsupported format")
    void testInvalidVenueAndFormat() {
        assertThatThrownBy(() -> service.analyzeTossDecision("", "ODI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Venue");

        assertThatThrownBy(() -> service.analyzeTossDecision("Melbourne Cricket Ground", "THE_HUNDRED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported format");
    }

    // ─── 3. Synthetic Edge Case Unit Tests using StringReader ──────────────────

    private static final String CSV_HEADER =
            "match_id,date,year,format,match_type,venue,city,team1,team2,is_full_member_match,toss_winner,toss_decision," +
            "toss_winner_choice,first_innings_team,second_innings_team,winner,result_type,win_by_type,win_by_margin,method," +
            "is_super_over,innings_count,bat_first_won,chase_won,is_decisive,data_quality_flag\n";

    @Test
    @DisplayName("Deduplication: Duplicate match IDs should be ignored and counted in duplicatesSkipped")
    void testDuplicateMatchIdsDeduplication() throws Exception {
        String csv = CSV_HEADER +
                "101,2026-06-01,2026,t20i,T20,Test Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,10.0,,False,2,1,0,True,clean\n" +
                "101,2026-06-01,2026,t20i,T20,Test Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,10.0,,False,2,1,0,True,clean\n" +
                "102,2026-06-02,2026,t20i,T20,Test Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team B,normal,wickets,5.0,,False,2,0,1,True,clean\n";

        TossDecisionService isolatedService = new TossDecisionService();
        isolatedService.loadFromReader(new StringReader(csv));

        assertThat(isolatedService.getDuplicatesSkipped()).isEqualTo(1);
        TossDecisionResponse res = isolatedService.analyzeTossDecision("Test Oval", "T20I");
        assertThat(res.getTotalEligibleMatches()).isEqualTo(2);
        assertThat(res.getDecisiveMatches()).isEqualTo(2);
    }

    @Test
    @DisplayName("Two-year date filtering: Matches prior to referenceDate minus 2 years are excluded")
    void testTwoYearDateFiltering() throws Exception {
        String csv = CSV_HEADER +
                // Reference date will be 2026-07-01. Window is 2024-07-01 to 2026-07-01.
                "201,2023-01-01,2023,t20i,T20,Date Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,10.0,,False,2,1,0,True,clean\n" +
                "202,2024-06-30,2024,t20i,T20,Date Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,10.0,,False,2,1,0,True,clean\n" +
                "203,2024-07-01,2024,t20i,T20,Date Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,10.0,,False,2,1,0,True,clean\n" +
                "204,2026-07-01,2026,t20i,T20,Date Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team B,normal,wickets,4.0,,False,2,0,1,True,clean\n";

        TossDecisionService isolatedService = new TossDecisionService();
        isolatedService.loadFromReader(new StringReader(csv));

        assertThat(isolatedService.getLatestReliableDate()).isEqualTo("2026-07-01");

        TossDecisionResponse res = isolatedService.analyzeTossDecision("Date Oval", "T20I");
        // Matches 201 and 202 are before 2024-07-01, so only 203 and 204 are eligible
        assertThat(res.getTotalEligibleMatches()).isEqualTo(2);
        assertThat(res.getDecisiveMatches()).isEqualTo(2);
        assertThat(res.getBatFirstWins()).isEqualTo(1);
        assertThat(res.getChaseWins()).isEqualTo(1);
    }

    @Test
    @DisplayName("Non-decisive matches (no-result, draw, tie) must be excluded from win-rate denominators")
    void testNonDecisiveExclusionFromDenominator() throws Exception {
        String csv = CSV_HEADER +
                "301,2026-01-01,2026,t20i,T20,Rain Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,20.0,,False,2,1,0,True,clean\n" +
                "302,2026-01-02,2026,t20i,T20,Rain Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,30.0,,False,2,1,0,True,clean\n" +
                "303,2026-01-03,2026,t20i,T20,Rain Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,,no_result,none,0.0,,False,1,0,0,False,incomplete_single_inning\n" +
                "304,2026-01-04,2026,t20i,T20,Rain Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,,tie,none,0.0,,False,2,0,0,False,tie_unresolved\n" +
                "305,2026-01-05,2026,t20i,T20,Rain Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,,draw,none,0.0,,False,2,0,0,False,draw\n";

        TossDecisionService isolatedService = new TossDecisionService();
        isolatedService.setMinSampleSize(2);
        isolatedService.loadFromReader(new StringReader(csv));

        TossDecisionResponse res = isolatedService.analyzeTossDecision("Rain Oval", "T20I");

        assertThat(res.getTotalEligibleMatches()).isEqualTo(5);
        assertThat(res.getDecisiveMatches()).isEqualTo(2);
        assertThat(res.getBatFirstWins()).isEqualTo(2);
        assertThat(res.getChaseWins()).isEqualTo(0);
        // 2 bat wins out of 2 decisive = 100.0% (NOT 2 out of 5 = 40%)
        assertThat(res.getBatFirstWinRate()).isEqualTo(100.0);
        assertThat(res.getChaseWinRate()).isEqualTo(0.0);

        assertThat(res.getExcludedBreakdown().getTotalExcludedCount()).isEqualTo(3);
        assertThat(res.getExcludedBreakdown().getNoResultCount()).isEqualTo(1);
        assertThat(res.getExcludedBreakdown().getTieCount()).isEqualTo(1);
        assertThat(res.getExcludedBreakdown().getDrawCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Awarded matches must be excluded from tactical win-rate denominator and reported")
    void testAwardedMatchesExclusion() throws Exception {
        String csv = CSV_HEADER +
                "401,2026-02-01,2026,t20i,T20,Award Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,15.0,,False,2,1,0,True,clean\n" +
                "402,2026-02-02,2026,t20i,T20,Award Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team B,normal,wickets,6.0,,False,2,0,1,True,clean\n" +
                "403,2026-02-03,2026,t20i,T20,Award Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,awarded,awarded,0.0,Awarded,False,1,1,0,True,awarded_match\n";

        TossDecisionService isolatedService = new TossDecisionService();
        isolatedService.setMinSampleSize(2);
        isolatedService.loadFromReader(new StringReader(csv));

        TossDecisionResponse res = isolatedService.analyzeTossDecision("Award Oval", "T20I");

        assertThat(res.getTotalEligibleMatches()).isEqualTo(3);
        assertThat(res.getDecisiveMatches()).isEqualTo(2); // Match 403 excluded
        assertThat(res.getExcludedBreakdown().getAwardedCount()).isEqualTo(1);
        assertThat(res.getExcludedBreakdown().getTotalExcludedCount()).isEqualTo(1);
        assertThat(res.getWarnings()).anyMatch(w -> w.contains("Excluded 1 awarded/forfeited match"));
    }

    @Test
    @DisplayName("Sample size below threshold should return INSUFFICIENT_DATA")
    void testInsufficientSampleSize() throws Exception {
        String csv = CSV_HEADER +
                "501,2026-03-01,2026,odi,ODI,Small Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,50.0,,False,2,1,0,True,clean\n" +
                "502,2026-03-02,2026,odi,ODI,Small Oval,,Team A,Team B,True,Team A,bat,Team A:bat,Team A,Team B,Team A,normal,runs,30.0,,False,2,1,0,True,clean\n";

        TossDecisionService isolatedService = new TossDecisionService();
        isolatedService.setMinSampleSize(5);
        isolatedService.loadFromReader(new StringReader(csv));

        TossDecisionResponse res = isolatedService.analyzeTossDecision("Small Oval", "ODI");
        assertThat(res.getDecisiveMatches()).isEqualTo(2);
        assertThat(res.getRecommendation()).isEqualTo(TossRecommendation.INSUFFICIENT_DATA);
        assertThat(res.getExplanation()).contains("minimum sample size of 5");
    }

    @Test
    @DisplayName("Zero matches found for venue should return INSUFFICIENT_DATA cleanly")
    void testZeroMatchesForVenue() {
        TossDecisionResponse res = service.analyzeTossDecision("Non Existent Stadium", "ODI");
        assertThat(res.getTotalEligibleMatches()).isEqualTo(0);
        assertThat(res.getDecisiveMatches()).isEqualTo(0);
        assertThat(res.getBatFirstWinRate()).isEqualTo(0.0);
        assertThat(res.getChaseWinRate()).isEqualTo(0.0);
        assertThat(res.getRecommendation()).isEqualTo(TossRecommendation.INSUFFICIENT_DATA);
    }
}
