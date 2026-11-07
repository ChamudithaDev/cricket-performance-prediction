package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.toss.*;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * TossDecisionService
 *
 * Provides historical match-level venue toss analytics and recommendations
 * derived from cricsheet_matches.csv.
 *
 * Analysis rules:
 * - Rolling two-year window ending on the latest reliable date in the dataset.
 * - Completed decisive matches only are included in win-rate denominators.
 * - Awarded/forfeited matches are excluded from tactical win rates and reported.
 * - Non-decisive matches (no-results, draws, unresolved ties) are tracked and excluded.
 * - Deduplicates by match_id.
 * - Evaluates win rate differential against configurable sample size and threshold.
 */
@Slf4j
@Service
public class TossDecisionService {

    public static final Set<String> SUPPORTED_FORMATS = Set.of("ODI", "T20I", "TEST");

    @Getter
    @Setter
    @Value("${analytics.cricsheet-matches-csv:../ml-service/data/cricsheet_matches.csv}")
    private String matchesCsvPath = "../ml-service/data/cricsheet_matches.csv";

    @Getter
    @Setter
    @Value("${analytics.toss-advisor.min-sample-size:5}")
    private int minSampleSize = 5;

    @Getter
    @Setter
    @Value("${analytics.toss-advisor.threshold-pp:5.0}")
    private double thresholdPercentagePoints = 5.0;

    @Getter
    @Setter
    @Value("${analytics.toss-advisor.window-years:2}")
    private int windowYears = 2;

    private final List<MatchRecord> allMatches = new CopyOnWriteArrayList<>();
    private final Map<String, Set<String>> venuesByFormat = new HashMap<>();
    private final Set<String> allVenues = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    @Getter
    private LocalDate latestReliableDate = null;

    @Getter
    private int duplicatesSkipped = 0;

    @Getter
    private int malformedSkipped = 0;

    public record MatchRecord(
            String matchId,
            LocalDate date,
            int year,
            String format,
            String matchType,
            String venue,
            String city,
            String team1,
            String team2,
            boolean isFullMemberMatch,
            String tossWinner,
            String tossDecision,
            String firstInningsTeam,
            String secondInningsTeam,
            String winner,
            String resultType,
            String winByType,
            double winByMargin,
            String method,
            boolean isSuperOver,
            int inningsCount,
            int batFirstWon,
            int chaseWon,
            boolean isDecisive,
            String dataQualityFlag
    ) {}

    @PostConstruct
    public void init() {
        loadDataFromConfiguredPath();
    }

    public synchronized void loadDataFromConfiguredPath() {
        Path path = resolveCsvPath(matchesCsvPath);
        log.info("[TossAdvisor] Loading Cricsheet matches CSV from: {}", path);
        if (!path.toFile().exists()) {
            log.warn("[TossAdvisor] Matches CSV not found at: {}. Service will operate in empty mode.", path);
            return;
        }
        try (Reader reader = new FileReader(path.toFile())) {
            loadFromReader(reader);
        } catch (IOException e) {
            log.error("[TossAdvisor] Failed to read matches CSV: {}", e.getMessage(), e);
        }
    }

    public synchronized void loadFromReader(Reader reader) throws IOException {
        allMatches.clear();
        venuesByFormat.clear();
        allVenues.clear();
        duplicatesSkipped = 0;
        malformedSkipped = 0;
        latestReliableDate = null;

        for (String fmt : SUPPORTED_FORMATS) {
            venuesByFormat.put(fmt.toLowerCase(Locale.ENGLISH), new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
        }

        Set<String> seenMatchIds = new HashSet<>();
        LocalDate maxDate = null;

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
                .parse(reader)) {

            for (CSVRecord r : parser) {
                try {
                    String matchId = r.get("match_id");
                    if (matchId == null || matchId.isBlank()) {
                        malformedSkipped++;
                        continue;
                    }
                    matchId = matchId.trim();

                    if (seenMatchIds.contains(matchId)) {
                        duplicatesSkipped++;
                        continue;
                    }

                    String dateStr = r.get("date");
                    LocalDate matchDate = LocalDate.parse(dateStr);

                    String fmt = r.get("format").trim().toLowerCase(Locale.ENGLISH);
                    String venue = r.get("venue").trim();

                    int year = safeInt(r.isMapped("year") ? r.get("year") : null, matchDate.getYear());
                    String matchType = r.isMapped("match_type") ? r.get("match_type") : "";
                    String city = r.isMapped("city") ? r.get("city") : "";
                    String team1 = r.isMapped("team1") ? r.get("team1") : "";
                    String team2 = r.isMapped("team2") ? r.get("team2") : "";
                    boolean isFm = safeBool(r.isMapped("is_full_member_match") ? r.get("is_full_member_match") : null);
                    String tossWinner = r.isMapped("toss_winner") ? r.get("toss_winner") : "";
                    String tossDecision = r.isMapped("toss_decision") ? r.get("toss_decision") : "";
                    String firstInn = r.isMapped("first_innings_team") ? r.get("first_innings_team") : "";
                    String secondInn = r.isMapped("second_innings_team") ? r.get("second_innings_team") : "";
                    String winner = r.isMapped("winner") ? r.get("winner") : "";
                    String resultType = r.isMapped("result_type") ? r.get("result_type") : "normal";
                    String winByType = r.isMapped("win_by_type") ? r.get("win_by_type") : "none";
                    double winByMargin = safeDouble(r.isMapped("win_by_margin") ? r.get("win_by_margin") : null);
                    String method = r.isMapped("method") ? r.get("method") : "";
                    boolean isSuperOver = safeBool(r.isMapped("is_super_over") ? r.get("is_super_over") : null);
                    int inningsCount = safeInt(r.isMapped("innings_count") ? r.get("innings_count") : null, 2);
                    int batFirstWon = safeInt(r.isMapped("bat_first_won") ? r.get("bat_first_won") : null, 0);
                    int chaseWon = safeInt(r.isMapped("chase_won") ? r.get("chase_won") : null, 0);
                    boolean isDecisive = safeBool(r.isMapped("is_decisive") ? r.get("is_decisive") : null);
                    String qualityFlag = r.isMapped("data_quality_flag") ? r.get("data_quality_flag") : "clean";

                    MatchRecord record = new MatchRecord(
                            matchId, matchDate, year, fmt, matchType, venue, city, team1, team2, isFm,
                            tossWinner, tossDecision, firstInn, secondInn, winner, resultType,
                            winByType, winByMargin, method, isSuperOver, inningsCount,
                            batFirstWon, chaseWon, isDecisive, qualityFlag
                    );

                    seenMatchIds.add(matchId);
                    allMatches.add(record);

                    if (maxDate == null || matchDate.isAfter(maxDate)) {
                        maxDate = matchDate;
                    }

                    allVenues.add(venue);
                    Set<String> fmtVenues = venuesByFormat.get(fmt);
                    if (fmtVenues != null) {
                        fmtVenues.add(venue);
                    }

                } catch (DateTimeParseException | IllegalArgumentException ex) {
                    malformedSkipped++;
                }
            }
        }

        latestReliableDate = maxDate;
        log.info("[TossAdvisor] Loaded {} matches. Duplicates skipped: {}, Malformed skipped: {}. Reference date: {}",
                allMatches.size(), duplicatesSkipped, malformedSkipped, latestReliableDate);
    }

    public List<String> getAvailableVenues(String format) {
        if (format == null || format.isBlank() || "ALL".equalsIgnoreCase(format)) {
            return new ArrayList<>(allVenues);
        }
        String normFmt = format.trim().toLowerCase(Locale.ENGLISH);
        Set<String> set = venuesByFormat.get(normFmt);
        if (set == null) {
            throw new IllegalArgumentException("Unsupported format: " + format + ". Allowed formats: " + SUPPORTED_FORMATS);
        }
        return new ArrayList<>(set);
    }

    public TossDecisionResponse analyzeTossDecision(String venue, String format) {
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException("Venue parameter is required and cannot be blank.");
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Format parameter is required. Supported formats: " + SUPPORTED_FORMATS);
        }

        String normFormat = format.trim().toUpperCase(Locale.ENGLISH);
        if (!SUPPORTED_FORMATS.contains(normFormat)) {
            throw new IllegalArgumentException("Unsupported format '" + format + "'. Supported formats are: " + SUPPORTED_FORMATS);
        }
        String fmtKey = normFormat.toLowerCase(Locale.ENGLISH);
        String trimmedVenue = venue.trim();

        LocalDate refDate = (latestReliableDate != null) ? latestReliableDate : LocalDate.now();
        LocalDate windowStart = refDate.minusYears(windowYears);
        LocalDate windowEnd = refDate;

        List<MatchRecord> eligibleMatches = allMatches.stream()
                .filter(m -> m.format().equalsIgnoreCase(fmtKey))
                .filter(m -> m.venue().equalsIgnoreCase(trimmedVenue))
                .filter(m -> !m.date().isBefore(windowStart) && !m.date().isAfter(windowEnd))
                .sorted(Comparator.comparing(MatchRecord::date).reversed())
                .toList();

        int totalEligible = eligibleMatches.size();
        int awardedCount = 0;
        int noResultCount = 0;
        int drawCount = 0;
        int tieCount = 0;
        int otherIncompleteCount = 0;

        List<MatchRecord> decisiveMatchesList = new ArrayList<>();
        List<TossMatchSummaryDto> matchSummaries = new ArrayList<>();

        for (MatchRecord m : eligibleMatches) {
            boolean awarded = isAwardedMatch(m);
            if (awarded) {
                awardedCount++;
            } else if (!m.isDecisive()) {
                String rt = m.resultType().toLowerCase(Locale.ENGLISH);
                if ("no_result".equals(rt)) {
                    noResultCount++;
                } else if ("draw".equals(rt)) {
                    drawCount++;
                } else if ("tie".equals(rt)) {
                    tieCount++;
                } else {
                    otherIncompleteCount++;
                }
            } else {
                decisiveMatchesList.add(m);
            }

            matchSummaries.add(TossMatchSummaryDto.builder()
                    .matchId(m.matchId())
                    .date(m.date().toString())
                    .team1(m.team1())
                    .team2(m.team2())
                    .tossWinner(m.tossWinner())
                    .tossDecision(m.tossDecision())
                    .firstInningsTeam(m.firstInningsTeam())
                    .secondInningsTeam(m.secondInningsTeam())
                    .winner(m.winner())
                    .winByType(m.winByType())
                    .winByMargin(m.winByMargin())
                    .isDecisive(m.isDecisive() && !awarded)
                    .resultType(m.resultType())
                    .dataQualityFlag(m.dataQualityFlag())
                    .batFirstWon(m.batFirstWon())
                    .chaseWon(m.chaseWon())
                    .build());
        }

        int decisiveCount = decisiveMatchesList.size();
        int totalExcluded = totalEligible - decisiveCount;

        int batFirstWins = decisiveMatchesList.stream().mapToInt(MatchRecord::batFirstWon).sum();
        int chaseWins = decisiveMatchesList.stream().mapToInt(MatchRecord::chaseWon).sum();

        double batFirstRate = 0.0;
        double chaseRate = 0.0;
        double diff = 0.0;

        if (decisiveCount > 0) {
            batFirstRate = Math.round((batFirstWins * 1000.0) / decisiveCount) / 10.0;
            chaseRate = Math.round((chaseWins * 1000.0) / decisiveCount) / 10.0;
            diff = Math.round((batFirstRate - chaseRate) * 10.0) / 10.0;
        }

        TossRecommendation recommendation;
        if (decisiveCount < minSampleSize) {
            recommendation = TossRecommendation.INSUFFICIENT_DATA;
        } else if (diff >= thresholdPercentagePoints) {
            recommendation = TossRecommendation.BAT_FIRST;
        } else if (diff <= -thresholdPercentagePoints) {
            recommendation = TossRecommendation.FIELD_FIRST;
        } else {
            recommendation = TossRecommendation.NO_CLEAR_ADVANTAGE;
        }

        List<String> warnings = new ArrayList<>();
        if (awardedCount > 0) {
            warnings.add(String.format("Excluded %d awarded/forfeited match(es) from tactical win-rate denominator.", awardedCount));
        }
        if (totalExcluded > awardedCount) {
            warnings.add(String.format("Excluded %d non-decisive match(es) (no-results: %d, draws: %d, ties: %d).",
                    (totalExcluded - awardedCount), noResultCount, drawCount, tieCount));
        }
        if (decisiveCount < minSampleSize) {
            warnings.add(String.format("Sample size (%d decisive match%s) is below the minimum threshold of %d. Recommendation flagged as INSUFFICIENT_DATA.",
                    decisiveCount, decisiveCount == 1 ? "" : "es", minSampleSize));
        }

        String explanation = buildExplanation(
                recommendation, trimmedVenue, normFormat,
                windowStart, windowEnd, decisiveCount, minSampleSize,
                batFirstWins, chaseWins, batFirstRate, chaseRate, diff, thresholdPercentagePoints
        );

        TossExcludedBreakdown excludedBreakdown = TossExcludedBreakdown.builder()
                .awardedCount(awardedCount)
                .noResultCount(noResultCount)
                .drawCount(drawCount)
                .tieCount(tieCount)
                .otherIncompleteCount(otherIncompleteCount)
                .totalExcludedCount(totalExcluded)
                .build();

        return TossDecisionResponse.builder()
                .venue(trimmedVenue)
                .format(normFormat)
                .referenceDate(refDate.toString())
                .windowStartDate(windowStart.toString())
                .windowEndDate(windowEnd.toString())
                .windowYears(windowYears)
                .totalEligibleMatches(totalEligible)
                .decisiveMatches(decisiveCount)
                .batFirstWins(batFirstWins)
                .chaseWins(chaseWins)
                .batFirstWinRate(batFirstRate)
                .chaseWinRate(chaseRate)
                .percentagePointDifference(diff)
                .recommendation(recommendation)
                .recommendationLabel(recommendation.getLabel())
                .explanation(explanation)
                .minSampleSize(minSampleSize)
                .thresholdPercentagePoints(thresholdPercentagePoints)
                .excludedBreakdown(excludedBreakdown)
                .warnings(warnings)
                .recentMatches(matchSummaries)
                .build();
    }

    private boolean isAwardedMatch(MatchRecord m) {
        return "awarded".equalsIgnoreCase(m.resultType())
                || "Awarded".equalsIgnoreCase(m.method())
                || "awarded_match".equalsIgnoreCase(m.dataQualityFlag());
    }

    private String buildExplanation(
            TossRecommendation rec, String venue, String format,
            LocalDate start, LocalDate end, int decisiveCount, int minSample,
            int batWins, int chaseWins, double batRate, double chaseRate, double diff, double threshold) {

        String dateRangeStr = String.format("%s to %s", start, end);

        switch (rec) {
            case INSUFFICIENT_DATA:
                return String.format(
                        "Only %d decisive %s match%s recorded at %s in the 2-year analysis window (%s). " +
                        "A minimum sample size of %d matches is required to generate a statistically reliable recommendation. " +
                        "Historical sample size is too limited to draw confident tactical conclusions.",
                        decisiveCount, format, decisiveCount == 1 ? " was" : "es were",
                        venue, dateRangeStr, minSample
                );

            case BAT_FIRST:
                return String.format(
                        "Teams batting first have won %d of %d decisive matches (%.1f%%) compared to chasing teams winning %d of %d (%.1f%%) " +
                        "at %s in %s matches over the 2-year window (%s), giving a +%.1f percentage-point advantage to setting a target. " +
                        "Historical trends indicate scoreboard pressure and pitch wear favor batting first here. " +
                        "Note: Historical win rates reflect prior conditions and do not guarantee future match outcomes.",
                        batWins, decisiveCount, batRate, chaseWins, decisiveCount, chaseRate,
                        venue, format, dateRangeStr, diff
                );

            case FIELD_FIRST:
                return String.format(
                        "Teams fielding first have won %d of %d decisive matches (%.1f%%) compared to batting-first teams winning %d of %d (%.1f%%) " +
                        "at %s in %s matches over the 2-year window (%s), giving a +%.1f percentage-point advantage to chasing. " +
                        "Conditions such as evening dew or a pitch that settles under lights historically favor chasing at this venue. " +
                        "Note: Historical win rates reflect prior conditions and do not guarantee future match outcomes.",
                        chaseWins, decisiveCount, chaseRate, batWins, decisiveCount, batRate,
                        venue, format, dateRangeStr, Math.abs(diff)
                );

            case NO_CLEAR_ADVANTAGE:
            default:
                return String.format(
                        "Across %d decisive %s matches at %s (%s), batting first produced %d wins (%.1f%%) and chasing produced %d wins (%.1f%%). " +
                        "The absolute differential (%.1f pp) is below the significance threshold of %.1f pp, indicating no statistically dominant bias. " +
                        "Captains should assess overhead weather, pitch moisture, and specific team balance when deciding.",
                        decisiveCount, format, venue, dateRangeStr,
                        batWins, batRate, chaseWins, chaseRate, Math.abs(diff), threshold
                );
        }
    }

    private Path resolveCsvPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = "../ml-service/data/cricsheet_matches.csv";
        }
        Path p = Paths.get(configuredPath);
        if (p.toFile().exists()) return p.toAbsolutePath();
        if (configuredPath.startsWith("../")) {
            Path alt = Paths.get(configuredPath.substring(3));
            if (alt.toFile().exists()) return alt.toAbsolutePath();
        }
        Path alt2 = Paths.get("..", configuredPath);
        if (alt2.toFile().exists()) return alt2.toAbsolutePath();
        return p.toAbsolutePath();
    }

    private int safeInt(String val, int def) {
        if (val == null || val.isBlank()) return def;
        try {
            return (int) Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private double safeDouble(String val) {
        if (val == null || val.isBlank()) return 0.0;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private boolean safeBool(String val) {
        if (val == null || val.isBlank()) return false;
        String s = val.trim().toLowerCase(Locale.ENGLISH);
        return "true".equals(s) || "1".equals(s);
    }
}
