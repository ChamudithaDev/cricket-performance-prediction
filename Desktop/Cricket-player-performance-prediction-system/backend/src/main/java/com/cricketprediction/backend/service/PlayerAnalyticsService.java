package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.analytics.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PlayerAnalyticsService
 *
 * Loads batting_performance.csv and bowling_performance.csv once at startup
 * and provides query methods for the analytics API.
 *
 * All metrics are derived directly from the CSV data — no values are invented.
 * CSV columns:
 *   batting: match_id, format, date, player, team, opposition, venue,
 *            runs, balls_faced, fours, sixes, strike_rate, not_out, dismissal_kind
 *   bowling: match_id, format, date, player, team, opposition, venue,
 *            overs, runs_conceded, wickets, economy
 *
 * Format values in the CSVs: odi, t20i, test (all lowercase)
 * Overs representation: "4.3" means 4 overs + 3 legal balls (not 4.3 decimal overs).
 *   True decimal overs = floor(overs) + (overs mod 1) * 10 / 6
 */
@Slf4j
@Service
public class PlayerAnalyticsService {

    /** "ALL" sentinel — return data across every format. */
    public static final String FORMAT_ALL = "ALL";

    @Value("${analytics.batting-csv:../ml-service/data/batting_performance.csv}")
    private String battingCsvPath;

    @Value("${analytics.bowling-csv:../ml-service/data/bowling_performance.csv}")
    private String bowlingCsvPath;

    /** In-memory store of all batting rows keyed by lower-case player name. */
    private final Map<String, List<BattingRow>> battingData = new HashMap<>();

    /** In-memory store of all bowling rows keyed by lower-case player name. */
    private final Map<String, List<BowlingRow>> bowlingData = new HashMap<>();

    /** Map of canonical player names preserved in their original casing. */
    private final Map<String, ParsedPlayer> canonicalPlayerMap = new LinkedHashMap<>();

    /** Set of all unique opposition teams in the dataset. */
    private final Set<String> allOppositions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    /** Set of all unique venues in the dataset. */
    private final Set<String> allVenues = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    // ─── Internal row records ──────────────────────────────────────────────────

    private record BattingRow(
            String date, String format, String player, String team,
            String opposition, String venue,
            int runs, int ballsFaced, int fours, int sixes,
            double strikeRate, boolean notOut
    ) {}

    private record BowlingRow(
            String date, String format, String player, String team,
            String opposition, String venue,
            double overs,          // CSV value: 4.3 = 4 overs + 3 balls
            int runsConceded, int wickets,
            double economy         // from CSV; may be 0 when overs == 0
    ) {}

    public record ParsedPlayer(
            String originalName,
            String normalizedName,
            List<String> tokens,
            List<String> surnameCandidates,
            List<String> forenameTokens,
            List<Character> initials
    ) {
        public static ParsedPlayer parse(String original) {
            String norm = normalize(original);
            String[] tokens = norm.isEmpty() ? new String[0] : norm.split(" ");
            if (tokens.length == 0) {
                return new ParsedPlayer(original, norm, List.of(), List.of(), List.of(), List.of());
            }

            List<String> surnames = new ArrayList<>();
            surnames.add(tokens[tokens.length - 1]);
            if (tokens.length >= 2 && isCompoundParticle(tokens[tokens.length - 2])) {
                surnames.add(tokens[tokens.length - 2] + " " + tokens[tokens.length - 1]);
            }
            if (tokens.length >= 3 && isCompoundParticle(tokens[tokens.length - 3]) && isCompoundParticle(tokens[tokens.length - 2])) {
                surnames.add(tokens[tokens.length - 3] + " " + tokens[tokens.length - 2] + " " + tokens[tokens.length - 1]);
            }

            String longestSur = surnames.stream().max(Comparator.comparingInt(String::length)).orElse(tokens[tokens.length - 1]);
            int numSurTokens = longestSur.split(" ").length;
            int numForeTokens = Math.max(0, tokens.length - numSurTokens);

            List<String> foreTokens = new ArrayList<>();
            for (int i = 0; i < numForeTokens; i++) {
                foreTokens.add(tokens[i]);
            }

            String[] origTokens = original.trim().split("\\s+");
            List<Character> initials = new ArrayList<>();
            int foreTokenLimit = Math.min(origTokens.length - numSurTokens, origTokens.length);
            for (int i = 0; i < foreTokenLimit; i++) {
                String ot = origTokens[i].replace(".", "").replace(",", "");
                if (ot.isEmpty()) continue;
                if (ot.equals(ot.toUpperCase(Locale.ENGLISH)) && ot.length() <= 4) {
                    for (char c : ot.toLowerCase(Locale.ENGLISH).toCharArray()) {
                        if (Character.isLetter(c)) initials.add(c);
                    }
                } else {
                    char firstChar = ot.toLowerCase(Locale.ENGLISH).charAt(0);
                    if (Character.isLetter(firstChar)) initials.add(firstChar);
                }
            }

            return new ParsedPlayer(
                    original,
                    norm,
                    List.of(tokens),
                    surnames,
                    foreTokens,
                    initials
            );
        }
    }

    // ─── Startup data loading ──────────────────────────────────────────────────

    @PostConstruct
    public void loadData() {
        loadBattingCsv();
        loadBowlingCsv();
        log.info("[Analytics] Batting rows loaded: {} players", battingData.size());
        log.info("[Analytics] Bowling rows loaded: {} players", bowlingData.size());
        log.info("[Analytics] Canonical distinct players: {}", canonicalPlayerMap.size());
    }

    private Path resolveCsvPath(String configuredPath) {
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

    private void loadBattingCsv() {
        Path path = resolveCsvPath(battingCsvPath);
        log.info("[Analytics] Loading batting CSV from: {}", path);
        if (!path.toFile().exists()) { log.warn("[Analytics] Batting CSV file not found at: {}", path); return; }
        try (Reader reader = new FileReader(path.toFile());
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord r : parser) {
                String originalPlayer = r.get("player");
                String playerKey = normalize(originalPlayer);
                canonicalPlayerMap.computeIfAbsent(originalPlayer, ParsedPlayer::parse);
                String opp = r.get("opposition");
                if (opp != null && !opp.isBlank()) allOppositions.add(opp.trim());
                String ven = r.get("venue");
                if (ven != null && !ven.isBlank()) allVenues.add(ven.trim());
                BattingRow row = new BattingRow(
                        r.get("date"), normalizeFormat(r.get("format")),
                        originalPlayer, r.get("team"), r.get("opposition"), r.get("venue"),
                        safeInt(r.get("runs")), safeInt(r.get("balls_faced")),
                        safeInt(r.get("fours")), safeInt(r.get("sixes")),
                        safeDouble(r.get("strike_rate")),
                        "True".equalsIgnoreCase(r.get("not_out")));
                battingData.computeIfAbsent(playerKey, k -> new ArrayList<>()).add(row);
            }
        } catch (IOException e) { log.error("[Analytics] Failed to load batting CSV: {}", e.getMessage()); }
    }

    private void loadBowlingCsv() {
        Path path = resolveCsvPath(bowlingCsvPath);
        log.info("[Analytics] Loading bowling CSV from: {}", path);
        if (!path.toFile().exists()) { log.warn("[Analytics] Bowling CSV file not found at: {}", path); return; }
        try (Reader reader = new FileReader(path.toFile());
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord r : parser) {
                String originalPlayer = r.get("player");
                String playerKey = normalize(originalPlayer);
                canonicalPlayerMap.computeIfAbsent(originalPlayer, ParsedPlayer::parse);
                String opp = r.get("opposition");
                if (opp != null && !opp.isBlank()) allOppositions.add(opp.trim());
                String ven = r.get("venue");
                if (ven != null && !ven.isBlank()) allVenues.add(ven.trim());
                BowlingRow row = new BowlingRow(
                        r.get("date"), normalizeFormat(r.get("format")),
                        originalPlayer, r.get("team"), r.get("opposition"), r.get("venue"),
                        safeDouble(r.get("overs")),
                        safeInt(r.get("runs_conceded")),
                        safeInt(r.get("wickets")),
                        safeDouble(r.get("economy")));
                bowlingData.computeIfAbsent(playerKey, k -> new ArrayList<>()).add(row);
            }
        } catch (IOException e) { log.error("[Analytics] Failed to load bowling CSV: {}", e.getMessage()); }
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves player name and returns analytics for that player filtered by format.
     *
     * @param playerName partial or full player name (case-insensitive)
     * @param format     "ODI", "T20I", "TEST", or "ALL" (case-insensitive)
     * @param recentN    how many recent innings to include in recent-form (default 15)
     * @return analytics response or null if no data found
     */
    public PlayerAnalyticsResponse getAnalytics(String playerName, String format, int recentN) {
        String resolvedName = resolvePlayerName(playerName);
        if (resolvedName == null) return null;

        String lookupKey = normalize(resolvedName);
        List<BattingRow> allBatting = battingData.getOrDefault(lookupKey, List.of());
        List<BowlingRow> allBowling = bowlingData.getOrDefault(lookupKey, List.of());

        if (allBatting.isEmpty() && allBowling.isEmpty()) return null;

        // Normalise requested format
        String normFmt = normalizeFormat(format == null ? FORMAT_ALL : format.trim());
        boolean filterAll = FORMAT_ALL.equalsIgnoreCase(normFmt);

        // Format-filtered data
        List<BattingRow> batting = filterAll ? allBatting
                : allBatting.stream().filter(r -> r.format().equalsIgnoreCase(normFmt)).toList();
        List<BowlingRow> bowling = filterAll ? allBowling
                : allBowling.stream().filter(r -> r.format().equalsIgnoreCase(normFmt)).toList();

        // Determine role from ALL data (not just selected format)
        String role = determineRole(allBatting, allBowling);

        // Build per-format breakdown (uses ALL data, not filtered)
        List<FormatStats> formatBreakdown = buildFormatBreakdown(allBatting, allBowling);

        // Recent form (format-filtered)
        List<RecentFormEntry>        recentBat  = buildRecentBattingForm(batting, recentN);
        List<BowlingRecentFormEntry> recentBowl = buildRecentBowlingForm(bowling, recentN);

        // Coverage metadata
        DataCoverage coverage = buildCoverage(
                resolvedName, role, normFmt, filterAll,
                allBatting, allBowling, batting, bowling,
                recentBat, recentBowl);

        // Venue / opposition breakdowns (format-filtered)
        List<VenueStats>             venueBreak    = null;
        List<OppositionStats>        oppBreak      = null;
        List<BowlingVenueStats>      bowlVenueBreak = null;
        List<BowlingOppositionStats> bowlOppBreak   = null;

        if (isBatterRole(role)) {
            venueBreak = buildBattingVenueBreakdown(batting);
            oppBreak   = buildBattingOppositionBreakdown(batting);
        }
        if (isBowlerRole(role)) {
            bowlVenueBreak = buildBowlingVenueBreakdown(bowling);
            bowlOppBreak   = buildBowlingOppositionBreakdown(bowling);
        }

        // Single Player Role-Based Analytics Enhancements
        BattingAnalyticsSummary battingSummary = null;
        ScoreDistribution scoreDist = null;
        if (!batting.isEmpty() && (isBatterRole(role) || "UNKNOWN".equalsIgnoreCase(role))) {
            battingSummary = buildSinglePlayerBattingSummary(batting);
            scoreDist = buildScoreDistribution(batting);
        }

        BowlingAnalyticsSummary bowlingSummary = null;
        BowlingDistribution bowlingDist = null;
        if (!bowling.isEmpty() && (isBowlerRole(role) || "UNKNOWN".equalsIgnoreCase(role))) {
            bowlingSummary = buildSinglePlayerBowlingSummary(bowling);
            bowlingDist = buildBowlingDistribution(bowling);
        }

        SinglePlayerRecentFormSummary singleRecentSummary =
                buildSinglePlayerRecentFormSummary(batting, bowling, recentN, role);

        return PlayerAnalyticsResponse.builder()
                .playerName(resolvedName)
                .role(role)
                .selectedFormat(filterAll ? FORMAT_ALL : normFmt.toUpperCase())
                .coverage(coverage)
                .totalBattingInnings(allBatting.size())
                .totalBowlingInnings(allBowling.size())
                .recentBattingForm(recentBat)
                .recentBowlingForm(recentBowl)
                .formatBreakdown(formatBreakdown)
                .venueBreakdown(venueBreak)
                .oppositionBreakdown(oppBreak)
                .bowlingVenueBreakdown(bowlVenueBreak)
                .bowlingOppositionBreakdown(bowlOppBreak)
                .battingSummary(battingSummary)
                .bowlingSummary(bowlingSummary)
                .scoreDistribution(scoreDist)
                .bowlingDistribution(bowlingDist)
                .recentFormSummary(singleRecentSummary)
                .build();
    }

    /**
     * Backward-compatible overload (format = ALL).
     */
    public PlayerAnalyticsResponse getAnalytics(String playerName, int recentN) {
        return getAnalytics(playerName, FORMAT_ALL, recentN);
    }

    /**
     * Lists all player names available in the dataset (for autocomplete).
     */
    public List<String> listAvailablePlayers() {
        return canonicalPlayerMap.values().stream()
                .map(ParsedPlayer::originalName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Lists all unique venues available in the dataset.
     */
    public List<String> getAllVenues() {
        return new ArrayList<>(allVenues);
    }

    /**
     * Lists all unique opposition teams available in the dataset.
     */
    public List<String> getAllOppositions() {
        return new ArrayList<>(allOppositions);
    }

    /**
     * Compares two players across the requested format.
     *
     * @param player1Query name of player 1 (resolvable via fuzzy match)
     * @param player2Query name of player 2 (resolvable via fuzzy match)
     * @param format       "ALL", "ODI", "T20I", or "TEST"
     * @param recentN      number of recent matches for trendlines/recent form
     * @return PlayerComparisonResponse
     * @throws IllegalArgumentException if players are missing, cannot be resolved, or are identical
     */
    public PlayerComparisonResponse comparePlayers(String player1Query, String player2Query, String format, int recentN) {
        return comparePlayers(player1Query, player2Query, format, recentN, null, null);
    }

    /**
     * Compares two players across the requested format, optional context filters, and recent window.
     *
     * @param player1Query name of player 1 (resolvable via fuzzy match)
     * @param player2Query name of player 2 (resolvable via fuzzy match)
     * @param format       "ALL", "ODI", "T20I", or "TEST"
     * @param recentN      number of recent matches for trendlines/recent form (5, 10, or 15)
     * @param opposition   optional opposition name to filter context
     * @param venue        optional venue name to filter context
     * @return PlayerComparisonResponse
     * @throws IllegalArgumentException if players are missing, cannot be resolved, or are identical
     */
    public PlayerComparisonResponse comparePlayers(
            String player1Query, String player2Query, String format, int recentN,
            String opposition, String venue) {

        if (player1Query == null || player1Query.trim().isEmpty()) {
            throw new IllegalArgumentException("Player 1 name must be provided.");
        }
        if (player2Query == null || player2Query.trim().isEmpty()) {
            throw new IllegalArgumentException("Player 2 name must be provided.");
        }

        String resolved1 = resolvePlayerName(player1Query.trim());
        if (resolved1 == null) {
            throw new IllegalArgumentException("No historical data found for player 1: \"" + player1Query + "\"");
        }
        String resolved2 = resolvePlayerName(player2Query.trim());
        if (resolved2 == null) {
            throw new IllegalArgumentException("No historical data found for player 2: \"" + player2Query + "\"");
        }

        if (resolved1.equalsIgnoreCase(resolved2)) {
            throw new IllegalArgumentException("Cannot compare a player with themselves: \"" + resolved1 + "\". Please select two distinct players.");
        }

        String normFmt = normalizeFormat(format == null ? FORMAT_ALL : format.trim());
        String displayFormat = FORMAT_ALL.equalsIgnoreCase(normFmt) ? FORMAT_ALL : normFmt.toUpperCase();
        int safeRecentN = Math.max(1, Math.min(50, recentN <= 0 ? 15 : recentN));

        String cleanOpposition = (opposition != null && !opposition.trim().isEmpty()) ? opposition.trim() : null;
        String cleanVenue = (venue != null && !venue.trim().isEmpty()) ? venue.trim() : null;

        PlayerAnalyticsResponse p1 = getAnalytics(resolved1, normFmt, safeRecentN);
        PlayerAnalyticsResponse p2 = getAnalytics(resolved2, normFmt, safeRecentN);

        // Determine comparison scope based on roles
        String role1 = p1 != null ? p1.getRole() : "UNKNOWN";
        String role2 = p2 != null ? p2.getRole() : "UNKNOWN";

        boolean p1Bats = isBatterRole(role1);
        boolean p2Bats = isBatterRole(role2);
        boolean p1Bowls = isBowlerRole(role1);
        boolean p2Bowls = isBowlerRole(role2);

        String scope;
        String explanation;

        if (p1Bats && p2Bats && p1Bowls && p2Bowls) {
            scope = "BOTH";
            explanation = "Both " + resolved1 + " and " + resolved2 + " are active all-rounders. Both batting and bowling facets are compared side-by-side.";
        } else if (p1Bats && p2Bats && !p1Bowls && !p2Bowls) {
            scope = "BATTING_ONLY";
            explanation = "Both players are recognized batsmen. Comparison focuses on batting scoring rate, totals, boundaries, and consistency.";
        } else if (!p1Bats && !p2Bats && p1Bowls && p2Bowls) {
            scope = "BOWLING_ONLY";
            explanation = "Both players are recognized bowlers. Comparison focuses on wickets, economy rates, bowling averages, and spell consistency.";
        } else if ((p1Bats && p2Bowls && !p1Bowls && !p2Bats) || (!p1Bats && p2Bats && p1Bowls && !p2Bowls)) {
            scope = "MIXED_DISCIPLINES";
            explanation = String.format("%s (%s) and %s (%s) have contrasting primary roles. Direct cross-discipline comparison (e.g. runs vs wickets) is statistically invalid. Their primary disciplines are presented in their respective domains.",
                    resolved1, role1, resolved2, role2);
        } else if ((p1Bats && p2Bats) && (p1Bowls || p2Bowls)) {
            scope = "BOTH";
            explanation = String.format("Both players have batting records, while %s also contributes with the ball. Batting metrics are directly compared, with bowling stats detailed where applicable.",
                    p1Bowls && p2Bowls ? "both" : (p1Bowls ? resolved1 : resolved2));
        } else {
            scope = (p1Bats && p2Bats) ? "BATTING_ONLY" : ((p1Bowls && p2Bowls) ? "BOWLING_ONLY" : "MIXED_DISCIPLINES");
            explanation = String.format("Comparing %s (%s) and %s (%s) in %s format.", resolved1, role1, resolved2, role2, displayFormat);
        }

        // Build batting & bowling comparison summaries
        PlayerComparisonResponse.BattingComparisonSummary battingSummary =
                buildBattingComparisonSummary(resolved1, resolved2, normFmt, cleanOpposition, cleanVenue);
        PlayerComparisonResponse.BowlingComparisonSummary bowlingSummary =
                buildBowlingComparisonSummary(resolved1, resolved2, normFmt, cleanOpposition, cleanVenue);

        // Build recent form summary (window of last 5, 10, or 15 eligible innings)
        PlayerComparisonResponse.RecentFormSummary recentFormSummary =
                buildRecentFormSummary(resolved1, resolved2, normFmt, safeRecentN, cleanOpposition, cleanVenue, role1, role2, scope);

        // Build context info (active filters and common oppositions/venues)
        PlayerComparisonResponse.ContextInfo contextInfo =
                buildContextInfo(resolved1, resolved2, normFmt, cleanOpposition, cleanVenue, battingSummary, bowlingSummary);

        // Determine metric advantages (legacy string map and detailed map)
        AdvantageCalculationResult advantageResults =
                calculateMetricAdvantageDetails(battingSummary, bowlingSummary, resolved1, resolved2);

        // Generate evidence-based written interpretation
        String writtenInterpretation = generateEvidenceBasedInterpretation(
                resolved1, resolved2, role1, role2, displayFormat, scope,
                battingSummary, bowlingSummary, recentFormSummary, advantageResults.detailed);

        return PlayerComparisonResponse.builder()
                .player1Name(resolved1)
                .player2Name(resolved2)
                .format(displayFormat)
                .comparisonScope(scope)
                .scopeExplanation(explanation)
                .player1(p1)
                .player2(p2)
                .battingComparison(battingSummary)
                .bowlingComparison(bowlingSummary)
                .recentFormSummary(recentFormSummary)
                .contextInfo(contextInfo)
                .metricAdvantages(advantageResults.legacy)
                .metricAdvantagesDetailed(advantageResults.detailed)
                .writtenInterpretation(writtenInterpretation)
                .build();
    }

    private List<BattingRow> filterBattingRows(String player, String normFmt, String opposition, String venue) {
        boolean filterAll = FORMAT_ALL.equalsIgnoreCase(normFmt);
        return battingData.getOrDefault(normalize(player), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .filter(r -> opposition == null || r.opposition().equalsIgnoreCase(opposition) || normalize(r.opposition()).contains(normalize(opposition)))
                .filter(r -> venue == null || r.venue().equalsIgnoreCase(venue) || normalize(r.venue()).contains(normalize(venue)))
                .toList();
    }

    private List<BowlingRow> filterBowlingRows(String player, String normFmt, String opposition, String venue) {
        boolean filterAll = FORMAT_ALL.equalsIgnoreCase(normFmt);
        return bowlingData.getOrDefault(normalize(player), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .filter(r -> opposition == null || r.opposition().equalsIgnoreCase(opposition) || normalize(r.opposition()).contains(normalize(opposition)))
                .filter(r -> venue == null || r.venue().equalsIgnoreCase(venue) || normalize(r.venue()).contains(normalize(venue)))
                .toList();
    }

    private PlayerComparisonResponse.BattingComparisonSummary buildBattingComparisonSummary(
            String player1, String player2, String normFmt, String opposition, String venue) {

        List<BattingRow> b1 = filterBattingRows(player1, normFmt, opposition, venue);
        List<BattingRow> b2 = filterBattingRows(player2, normFmt, opposition, venue);

        int inn1 = b1.size();
        int inn2 = b2.size();
        int runs1 = b1.stream().mapToInt(BattingRow::runs).sum();
        int runs2 = b2.stream().mapToInt(BattingRow::runs).sum();
        int balls1 = b1.stream().mapToInt(BattingRow::ballsFaced).sum();
        int balls2 = b2.stream().mapToInt(BattingRow::ballsFaced).sum();
        int notOuts1 = (int) b1.stream().filter(BattingRow::notOut).count();
        int notOuts2 = (int) b2.stream().filter(BattingRow::notOut).count();
        int dismissals1 = inn1 - notOuts1;
        int dismissals2 = inn2 - notOuts2;
        int fours1 = b1.stream().mapToInt(BattingRow::fours).sum();
        int fours2 = b2.stream().mapToInt(BattingRow::fours).sum();
        int sixes1 = b1.stream().mapToInt(BattingRow::sixes).sum();
        int sixes2 = b2.stream().mapToInt(BattingRow::sixes).sum();
        int boundaryRuns1 = fours1 * 4 + sixes1 * 6;
        int boundaryRuns2 = fours2 * 4 + sixes2 * 6;
        int fifties1 = (int) b1.stream().filter(r -> r.runs() >= 50 && r.runs() < 100).count();
        int fifties2 = (int) b2.stream().filter(r -> r.runs() >= 50 && r.runs() < 100).count();
        int hundreds1 = (int) b1.stream().filter(r -> r.runs() >= 100).count();
        int hundreds2 = (int) b2.stream().filter(r -> r.runs() >= 100).count();

        Double rpi1 = inn1 > 0 ? round2((double) runs1 / inn1) : null;
        Double rpi2 = inn2 > 0 ? round2((double) runs2 / inn2) : null;

        // Official Batting Average = totalRuns / dismissals (null if dismissals == 0)
        Double avg1 = dismissals1 > 0 ? round2((double) runs1 / dismissals1) : null;
        Double avg2 = dismissals2 > 0 ? round2((double) runs2 / dismissals2) : null;

        Double sr1 = balls1 > 0 ? round2((double) runs1 / balls1 * 100.0) : null;
        Double sr2 = balls2 > 0 ? round2((double) runs2 / balls2 * 100.0) : null;

        Double bPct1 = runs1 > 0 ? round2((double) boundaryRuns1 / runs1 * 100.0) : null;
        Double bPct2 = runs2 > 0 ? round2((double) boundaryRuns2 / runs2 * 100.0) : null;

        Double bBallPct1 = balls1 > 0 ? round2((double) (fours1 + sixes1) / balls1 * 100.0) : null;
        Double bBallPct2 = balls2 > 0 ? round2((double) (fours2 + sixes2) / balls2 * 100.0) : null;

        Double stdDev1 = computeSampleStdDev(b1.stream().map(r -> (double) r.runs()).toList());
        Double stdDev2 = computeSampleStdDev(b2.stream().map(r -> (double) r.runs()).toList());
        Double cv1 = (stdDev1 != null && rpi1 != null && rpi1 > 0 && inn1 >= 3) ? round2((stdDev1 / rpi1) * 100.0) : null;
        Double cv2 = (stdDev2 != null && rpi2 != null && rpi2 > 0 && inn2 >= 3) ? round2((stdDev2 / rpi2) * 100.0) : null;

        BattingRow hsRow1 = b1.stream().max(Comparator.comparingInt(BattingRow::runs)).orElse(null);
        BattingRow hsRow2 = b2.stream().max(Comparator.comparingInt(BattingRow::runs)).orElse(null);
        Integer hs1 = hsRow1 != null ? hsRow1.runs() : null;
        Integer hs2 = hsRow2 != null ? hsRow2.runs() : null;
        Boolean hsNotOut1 = hsRow1 != null ? hsRow1.notOut() : null;
        Boolean hsNotOut2 = hsRow2 != null ? hsRow2.notOut() : null;

        return PlayerComparisonResponse.BattingComparisonSummary.builder()
                .innings1(inn1).innings2(inn2)
                .totalRuns1(runs1).totalRuns2(runs2)
                .ballsFaced1(balls1).ballsFaced2(balls2)
                .notOuts1(notOuts1).notOuts2(notOuts2)
                .dismissals1(dismissals1).dismissals2(dismissals2)
                .highestScore1(hs1).highestScore2(hs2)
                .highestScoreNotOut1(hsNotOut1).highestScoreNotOut2(hsNotOut2)
                .runsPerInnings1(rpi1).runsPerInnings2(rpi2)
                .battingAverage1(avg1).battingAverage2(avg2)
                .strikeRate1(sr1).strikeRate2(sr2)
                .fours1(fours1).fours2(fours2)
                .sixes1(sixes1).sixes2(sixes2)
                .boundaryRuns1(boundaryRuns1).boundaryRuns2(boundaryRuns2)
                .boundaryPercentage1(bPct1).boundaryPercentage2(bPct2)
                .boundaryBallPercentage1(bBallPct1).boundaryBallPercentage2(bBallPct2)
                .dotBallPercentage1(null).dotBallPercentage2(null) // Ball-by-ball dot ball data not recorded in CSV
                .fifties1(fifties1).fifties2(fifties2)
                .hundreds1(hundreds1).hundreds2(hundreds2)
                .stdDevRuns1(stdDev1).stdDevRuns2(stdDev2)
                .coefficientOfVariation1(cv1).coefficientOfVariation2(cv2)
                .consistencyNote("Consistency is evaluated via sample standard deviation and coefficient of variation (CV% = stdDev / mean * 100) of runs per innings. Lower CV indicates higher scoring consistency. Requires >= 3 innings.")
                .build();
    }

    private PlayerComparisonResponse.BowlingComparisonSummary buildBowlingComparisonSummary(
            String player1, String player2, String normFmt, String opposition, String venue) {

        List<BowlingRow> bw1 = filterBowlingRows(player1, normFmt, opposition, venue);
        List<BowlingRow> bw2 = filterBowlingRows(player2, normFmt, opposition, venue);

        int inn1 = bw1.size();
        int inn2 = bw2.size();
        double decOvers1 = round2(bw1.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum());
        double decOvers2 = round2(bw2.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum());

        // Valid balls bowled conversion: 4.2 overs = 4*6 + 2 = 26 balls
        int balls1 = bw1.stream().mapToInt(r -> {
            int fullOvers = (int) r.overs();
            int balls = (int) Math.round((r.overs() - fullOvers) * 10);
            return fullOvers * 6 + balls;
        }).sum();
        int balls2 = bw2.stream().mapToInt(r -> {
            int fullOvers = (int) r.overs();
            int balls = (int) Math.round((r.overs() - fullOvers) * 10);
            return fullOvers * 6 + balls;
        }).sum();

        int rc1 = bw1.stream().mapToInt(BowlingRow::runsConceded).sum();
        int rc2 = bw2.stream().mapToInt(BowlingRow::runsConceded).sum();
        int w1 = bw1.stream().mapToInt(BowlingRow::wickets).sum();
        int w2 = bw2.stream().mapToInt(BowlingRow::wickets).sum();

        Double wpi1 = inn1 > 0 ? round2((double) w1 / inn1) : null;
        Double wpi2 = inn2 > 0 ? round2((double) w2 / inn2) : null;

        Double econ1 = decOvers1 > 0 ? round2((double) rc1 / decOvers1) : null;
        Double econ2 = decOvers2 > 0 ? round2((double) rc2 / decOvers2) : null;

        // Bowling Average = runsConceded / wickets (null if wickets == 0)
        Double avg1 = w1 > 0 ? round2((double) rc1 / w1) : null;
        Double avg2 = w2 > 0 ? round2((double) rc2 / w2) : null;

        // Bowling Strike Rate = valid balls / wickets (null if wickets == 0)
        Double sr1 = w1 > 0 ? round2((double) balls1 / w1) : null;
        Double sr2 = w2 > 0 ? round2((double) balls2 / w2) : null;

        int fourW1 = (int) bw1.stream().filter(r -> r.wickets() == 4).count();
        int fourW2 = (int) bw2.stream().filter(r -> r.wickets() == 4).count();
        int fiveW1 = (int) bw1.stream().filter(r -> r.wickets() >= 5).count();
        int fiveW2 = (int) bw2.stream().filter(r -> r.wickets() >= 5).count();

        Double stdDevW1 = computeSampleStdDev(bw1.stream().map(r -> (double) r.wickets()).toList());
        Double stdDevW2 = computeSampleStdDev(bw2.stream().map(r -> (double) r.wickets()).toList());

        return PlayerComparisonResponse.BowlingComparisonSummary.builder()
                .innings1(inn1).innings2(inn2)
                .totalOvers1(decOvers1).totalOvers2(decOvers2)
                .validBallsBowled1(balls1).validBallsBowled2(balls2)
                .runsConceded1(rc1).runsConceded2(rc2)
                .totalWickets1(w1).totalWickets2(w2)
                .wicketsPerInnings1(wpi1).wicketsPerInnings2(wpi2)
                .economyRate1(econ1).economyRate2(econ2)
                .bowlingAverage1(avg1).bowlingAverage2(avg2)
                .bowlingStrikeRate1(sr1).bowlingStrikeRate2(sr2)
                .fourWickets1(fourW1).fourWickets2(fourW2)
                .fiveWickets1(fiveW1).fiveWickets2(fiveW2)
                .maidens1(null).maidens2(null) // Maidens not recorded in CSV
                .stdDevWickets1(stdDevW1).stdDevWickets2(stdDevW2)
                .build();
    }

    private PlayerComparisonResponse.RecentFormSummary buildRecentFormSummary(
            String player1, String player2, String normFmt, int recentN, String opposition, String venue) {
        return buildRecentFormSummary(player1, player2, normFmt, recentN, opposition, venue, "BATSMAN", "BATSMAN", "BATTING_ONLY");
    }

    private PlayerComparisonResponse.RecentFormSummary buildRecentFormSummary(
            String player1, String player2, String normFmt, int recentN, String opposition, String venue,
            String role1, String role2, String scope) {

        List<BattingRow> b1 = filterBattingRows(player1, normFmt, opposition, venue);
        List<BattingRow> b2 = filterBattingRows(player2, normFmt, opposition, venue);
        List<BowlingRow> bw1 = filterBowlingRows(player1, normFmt, opposition, venue);
        List<BowlingRow> bw2 = filterBowlingRows(player2, normFmt, opposition, venue);

        List<BattingRow> recentBat1 = getChronologicalRecent(b1, Comparator.comparing(BattingRow::date), recentN);
        List<BattingRow> recentBat2 = getChronologicalRecent(b2, Comparator.comparing(BattingRow::date), recentN);

        Double meanR1 = recentBat1.isEmpty() ? null : round2(recentBat1.stream().mapToInt(BattingRow::runs).average().orElse(0.0));
        Double meanR2 = recentBat2.isEmpty() ? null : round2(recentBat2.stream().mapToInt(BattingRow::runs).average().orElse(0.0));
        Double stdDevR1 = computeSampleStdDev(recentBat1.stream().map(r -> (double) r.runs()).toList());
        Double stdDevR2 = computeSampleStdDev(recentBat2.stream().map(r -> (double) r.runs()).toList());

        TrendResult runTrend1 = computeTrend(recentBat1.stream().map(r -> (double) r.runs()).toList(), 1.0);
        TrendResult runTrend2 = computeTrend(recentBat2.stream().map(r -> (double) r.runs()).toList(), 1.0);

        List<BowlingRow> recentBw1 = getChronologicalRecent(bw1, Comparator.comparing(BowlingRow::date), recentN);
        List<BowlingRow> recentBw2 = getChronologicalRecent(bw2, Comparator.comparing(BowlingRow::date), recentN);

        int bwInn1 = recentBw1.size();
        int bwInn2 = recentBw2.size();
        int totalW1 = recentBw1.stream().mapToInt(BowlingRow::wickets).sum();
        int totalW2 = recentBw2.stream().mapToInt(BowlingRow::wickets).sum();
        int totalRc1 = recentBw1.stream().mapToInt(BowlingRow::runsConceded).sum();
        int totalRc2 = recentBw2.stream().mapToInt(BowlingRow::runsConceded).sum();
        double totalDecOvers1 = round2(recentBw1.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum());
        double totalDecOvers2 = round2(recentBw2.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum());
        int totalBalls1 = recentBw1.stream().mapToInt(r -> {
            int fullOvers = (int) r.overs();
            int balls = (int) Math.round((r.overs() - fullOvers) * 10);
            return fullOvers * 6 + balls;
        }).sum();
        int totalBalls2 = recentBw2.stream().mapToInt(r -> {
            int fullOvers = (int) r.overs();
            int balls = (int) Math.round((r.overs() - fullOvers) * 10);
            return fullOvers * 6 + balls;
        }).sum();

        Double meanW1 = bwInn1 > 0 ? round2((double) totalW1 / bwInn1) : null;
        Double meanW2 = bwInn2 > 0 ? round2((double) totalW2 / bwInn2) : null;

        // Window rates derived from underlying totals
        Double econ1 = totalDecOvers1 > 0 ? round2((double) totalRc1 / totalDecOvers1) : null;
        Double econ2 = totalDecOvers2 > 0 ? round2((double) totalRc2 / totalDecOvers2) : null;
        Double avg1 = totalW1 > 0 ? round2((double) totalRc1 / totalW1) : null;
        Double avg2 = totalW2 > 0 ? round2((double) totalRc2 / totalW2) : null;
        Double sr1 = totalW1 > 0 ? round2((double) totalBalls1 / totalW1) : null;
        Double sr2 = totalW2 > 0 ? round2((double) totalBalls2 / totalW2) : null;

        int fourW1 = (int) recentBw1.stream().filter(r -> r.wickets() == 4).count();
        int fourW2 = (int) recentBw2.stream().filter(r -> r.wickets() == 4).count();
        int fiveW1 = (int) recentBw1.stream().filter(r -> r.wickets() >= 5).count();
        int fiveW2 = (int) recentBw2.stream().filter(r -> r.wickets() >= 5).count();

        Double stdDevW1 = computeSampleStdDev(recentBw1.stream().map(r -> (double) r.wickets()).toList());
        Double stdDevW2 = computeSampleStdDev(recentBw2.stream().map(r -> (double) r.wickets()).toList());

        TrendResult wktTrend1 = computeTrend(recentBw1.stream().map(r -> (double) r.wickets()).toList(), 0.15);
        TrendResult wktTrend2 = computeTrend(recentBw2.stream().map(r -> (double) r.wickets()).toList(), 0.15);

        Integer highR1 = recentBat1.isEmpty() ? null : recentBat1.stream().mapToInt(BattingRow::runs).max().orElse(0);
        Integer highR2 = recentBat2.isEmpty() ? null : recentBat2.stream().mapToInt(BattingRow::runs).max().orElse(0);
        Integer lowR1 = recentBat1.isEmpty() ? null : recentBat1.stream().mapToInt(BattingRow::runs).min().orElse(0);
        Integer lowR2 = recentBat2.isEmpty() ? null : recentBat2.stream().mapToInt(BattingRow::runs).min().orElse(0);

        String runAdvantage;
        Double runDiff = null;
        if (meanR1 != null && meanR2 != null) {
            runDiff = round2(Math.abs(meanR1 - meanR2));
            if (Math.abs(meanR1 - meanR2) < 0.05) {
                runAdvantage = "tie";
            } else if (meanR1 > meanR2) {
                runAdvantage = "player1";
            } else {
                runAdvantage = "player2";
            }
        } else {
            runAdvantage = "insufficient_data";
        }

        String warning1 = (recentBat1.size() > 0 && recentBat1.size() < 3) ? "Sample size too small (< 3 innings) for reliable form estimation." : null;
        String warning2 = (recentBat2.size() > 0 && recentBat2.size() < 3) ? "Sample size too small (< 3 innings) for reliable form estimation." : null;

        Integer highW1 = recentBw1.isEmpty() ? null : recentBw1.stream().mapToInt(BowlingRow::wickets).max().orElse(0);
        Integer highW2 = recentBw2.isEmpty() ? null : recentBw2.stream().mapToInt(BowlingRow::wickets).max().orElse(0);
        Integer lowW1 = recentBw1.isEmpty() ? null : recentBw1.stream().mapToInt(BowlingRow::wickets).min().orElse(0);
        Integer lowW2 = recentBw2.isEmpty() ? null : recentBw2.stream().mapToInt(BowlingRow::wickets).min().orElse(0);

        String wktAdvantage;
        Double wktDiff = null;
        if (meanW1 != null && meanW2 != null) {
            wktDiff = round2(Math.abs(meanW1 - meanW2));
            if (Math.abs(meanW1 - meanW2) < 0.05) {
                wktAdvantage = "tie";
            } else if (meanW1 > meanW2) {
                wktAdvantage = "player1";
            } else {
                wktAdvantage = "player2";
            }
        } else {
            wktAdvantage = "insufficient_data";
        }

        String bwlWarning1 = (bwInn1 > 0 && bwInn1 < 3) ? "Sample size too small (< 3 bowling innings) for reliable form estimation." : null;
        String bwlWarning2 = (bwInn2 > 0 && bwInn2 < 3) ? "Sample size too small (< 3 bowling innings) for reliable form estimation." : null;

        boolean isBowlingFocus = ("BOWLER".equals(role1) && "BOWLER".equals(role2))
                || ("ALL_ROUNDER".equals(role1) && "ALL_ROUNDER".equals(role2))
                || "BOWLING_ONLY".equals(scope);
        String focusDiscipline = isBowlingFocus ? "BOWLING" : "BATTING";

        return PlayerComparisonResponse.RecentFormSummary.builder()
                .recentN(recentN)
                .calculationBasis("Calculated strictly from eligible innings where player participated (batted or bowled in chronological order). Missing matches are not treated as zero scores.")
                .focusDiscipline(focusDiscipline)
                .battingInnings1(recentBat1.size())
                .battingInnings2(recentBat2.size())
                .meanRuns1(meanR1).meanRuns2(meanR2)
                .stdDevRuns1(stdDevR1).stdDevRuns2(stdDevR2)
                .highestRuns1(highR1).highestRuns2(highR2)
                .lowestRuns1(lowR1).lowestRuns2(lowR2)
                .recentAverageRunsAdvantage(runAdvantage)
                .recentAverageRunsDifference(runDiff)
                .sampleSizeWarning1(warning1)
                .sampleSizeWarning2(warning2)
                .runTrendDirection1(runTrend1.direction).runTrendDirection2(runTrend2.direction)
                .runTrendSlope1(runTrend1.slope).runTrendSlope2(runTrend2.slope)
                .bowlingInnings1(bwInn1)
                .bowlingInnings2(bwInn2)
                .totalWickets1(totalW1).totalWickets2(totalW2)
                .totalRunsConceded1(totalRc1).totalRunsConceded2(totalRc2)
                .totalOvers1(totalDecOvers1).totalOvers2(totalDecOvers2)
                .totalValidBalls1(totalBalls1).totalValidBalls2(totalBalls2)
                .meanWickets1(meanW1).meanWickets2(meanW2)
                .recentEconomyRate1(econ1).recentEconomyRate2(econ2)
                .recentBowlingAverage1(avg1).recentBowlingAverage2(avg2)
                .recentBowlingStrikeRate1(sr1).recentBowlingStrikeRate2(sr2)
                .fourWickets1(fourW1).fourWickets2(fourW2)
                .fiveWickets1(fiveW1).fiveWickets2(fiveW2)
                .stdDevWickets1(stdDevW1).stdDevWickets2(stdDevW2)
                .highestWickets1(highW1).highestWickets2(highW2)
                .lowestWickets1(lowW1).lowestWickets2(lowW2)
                .recentAverageWicketsAdvantage(wktAdvantage)
                .recentAverageWicketsDifference(wktDiff)
                .bowlingSampleSizeWarning1(bwlWarning1)
                .bowlingSampleSizeWarning2(bwlWarning2)
                .wicketTrendDirection1(wktTrend1.direction).wicketTrendDirection2(wktTrend2.direction)
                .wicketTrendSlope1(wktTrend1.slope).wicketTrendSlope2(wktTrend2.slope)
                .build();
    }

    private <T> List<T> getChronologicalRecent(List<T> list, Comparator<T> dateComp, int n) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .sorted(dateComp.reversed())
                .limit(n)
                .sorted(dateComp)
                .toList();
    }

    private PlayerComparisonResponse.ContextInfo buildContextInfo(
            String player1, String player2, String normFmt, String opposition, String venue,
            PlayerComparisonResponse.BattingComparisonSummary battingSummary,
            PlayerComparisonResponse.BowlingComparisonSummary bowlingSummary) {
        boolean filterAll = FORMAT_ALL.equalsIgnoreCase(normFmt);

        Set<String> opps1 = new HashSet<>();
        battingData.getOrDefault(normalize(player1), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BattingRow::opposition).forEach(opps1::add);
        bowlingData.getOrDefault(normalize(player1), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BowlingRow::opposition).forEach(opps1::add);

        Set<String> opps2 = new HashSet<>();
        battingData.getOrDefault(normalize(player2), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BattingRow::opposition).forEach(opps2::add);
        bowlingData.getOrDefault(normalize(player2), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BowlingRow::opposition).forEach(opps2::add);

        List<String> commonOpps = opps1.stream()
                .filter(opps2::contains)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        Set<String> venues1 = new HashSet<>();
        battingData.getOrDefault(normalize(player1), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BattingRow::venue).forEach(venues1::add);
        bowlingData.getOrDefault(normalize(player1), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BowlingRow::venue).forEach(venues1::add);

        Set<String> venues2 = new HashSet<>();
        battingData.getOrDefault(normalize(player2), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BattingRow::venue).forEach(venues2::add);
        bowlingData.getOrDefault(normalize(player2), List.of()).stream()
                .filter(r -> filterAll || r.format().equalsIgnoreCase(normFmt))
                .map(BowlingRow::venue).forEach(venues2::add);

        List<String> commonVenues = venues1.stream()
                .filter(venues2::contains)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        PlayerComparisonResponse.ContextualBattingStats ctxBat = null;
        PlayerComparisonResponse.ContextualBowlingStats ctxBowl = null;
        String warning = null;

        if (opposition != null || venue != null) {
            if (battingSummary != null) {
                ctxBat = PlayerComparisonResponse.ContextualBattingStats.builder()
                        .innings1(battingSummary.getInnings1())
                        .innings2(battingSummary.getInnings2())
                        .totalRuns1(battingSummary.getTotalRuns1())
                        .totalRuns2(battingSummary.getTotalRuns2())
                        .runsPerInnings1(battingSummary.getRunsPerInnings1())
                        .runsPerInnings2(battingSummary.getRunsPerInnings2())
                        .strikeRate1(battingSummary.getStrikeRate1())
                        .strikeRate2(battingSummary.getStrikeRate2())
                        .highestScore1(battingSummary.getHighestScore1())
                        .highestScore2(battingSummary.getHighestScore2())
                        .build();
            }

            if (bowlingSummary != null) {
                ctxBowl = PlayerComparisonResponse.ContextualBowlingStats.builder()
                        .innings1(bowlingSummary.getInnings1())
                        .innings2(bowlingSummary.getInnings2())
                        .totalWickets1(bowlingSummary.getTotalWickets1())
                        .totalWickets2(bowlingSummary.getTotalWickets2())
                        .economyRate1(bowlingSummary.getEconomyRate1())
                        .economyRate2(bowlingSummary.getEconomyRate2())
                        .bowlingAverage1(bowlingSummary.getBowlingAverage1())
                        .bowlingAverage2(bowlingSummary.getBowlingAverage2())
                        .bowlingStrikeRate1(bowlingSummary.getBowlingStrikeRate1())
                        .bowlingStrikeRate2(bowlingSummary.getBowlingStrikeRate2())
                        .build();
            }

            int batInn1 = battingSummary != null ? battingSummary.getInnings1() : 0;
            int batInn2 = battingSummary != null ? battingSummary.getInnings2() : 0;
            int bowlInn1 = bowlingSummary != null ? bowlingSummary.getInnings1() : 0;
            int bowlInn2 = bowlingSummary != null ? bowlingSummary.getInnings2() : 0;
            int totalRecords = batInn1 + batInn2 + bowlInn1 + bowlInn2;

            if (totalRecords == 0) {
                warning = "No matching records found for the selected venue/opposition filter.";
            } else if ((batInn1 + bowlInn1 < 3) || (batInn2 + bowlInn2 < 3)) {
                warning = "Limited contextual records (< 3 innings) available for one or both players. Performance under these conditions should be interpreted with caution.";
            }
        }

        return PlayerComparisonResponse.ContextInfo.builder()
                .oppositionFilter(opposition)
                .venueFilter(venue)
                .commonOppositions(commonOpps)
                .commonVenues(commonVenues)
                .allOppositions(new ArrayList<>(allOppositions))
                .allVenues(new ArrayList<>(allVenues))
                .contextualBatting(ctxBat)
                .contextualBowling(ctxBowl)
                .contextualSampleSizeWarning(warning)
                .build();
    }

    private record AdvantageCalculationResult(
            Map<String, String> legacy,
            Map<String, PlayerComparisonResponse.MetricAdvantageDetail> detailed
    ) {}

    private AdvantageCalculationResult calculateMetricAdvantageDetails(
            PlayerComparisonResponse.BattingComparisonSummary bat,
            PlayerComparisonResponse.BowlingComparisonSummary bowl,
            String p1Name, String p2Name) {

        Map<String, String> legacy = new LinkedHashMap<>();
        Map<String, PlayerComparisonResponse.MetricAdvantageDetail> detailed = new LinkedHashMap<>();

        // Batting metrics
        if (bat != null) {
            putAdvantage(legacy, detailed, "runs", evaluateAdvantage(p1Name, p2Name, "Total Runs", bat.getTotalRuns1(), bat.getTotalRuns2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "runsPerInnings", evaluateAdvantage(p1Name, p2Name, "Runs Per Innings", bat.getRunsPerInnings1(), bat.getRunsPerInnings2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "battingAverage", evaluateAdvantage(p1Name, p2Name, "Official Batting Average", bat.getBattingAverage1(), bat.getBattingAverage2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "battingStrikeRate", evaluateAdvantage(p1Name, p2Name, "Batting Strike Rate", bat.getStrikeRate1(), bat.getStrikeRate2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "boundaryPercentage", evaluateAdvantage(p1Name, p2Name, "Boundary Run %", bat.getBoundaryPercentage1(), bat.getBoundaryPercentage2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "boundaryBallPercentage", evaluateAdvantage(p1Name, p2Name, "Boundary Ball %", bat.getBoundaryBallPercentage1(), bat.getBoundaryBallPercentage2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "fours", evaluateAdvantage(p1Name, p2Name, "Fours", bat.getFours1(), bat.getFours2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "sixes", evaluateAdvantage(p1Name, p2Name, "Sixes", bat.getSixes1(), bat.getSixes2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "fifties", evaluateAdvantage(p1Name, p2Name, "Half-Centuries", bat.getFifties1(), bat.getFifties2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "hundreds", evaluateAdvantage(p1Name, p2Name, "Centuries", bat.getHundreds1(), bat.getHundreds2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "highestScore", evaluateAdvantage(p1Name, p2Name, "Highest Score", bat.getHighestScore1(), bat.getHighestScore2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "consistency", evaluateAdvantage(p1Name, p2Name, "Consistency (CV%)", bat.getCoefficientOfVariation1(), bat.getCoefficientOfVariation2(), "LOWER_VARIABILITY"));
            putAdvantage(legacy, detailed, "dotBallPercentage", evaluateAdvantage(p1Name, p2Name, "Dot Ball %", null, null, "HIGHER_IS_BETTER"));
        }

        // Bowling metrics
        if (bowl != null) {
            putAdvantage(legacy, detailed, "wickets", evaluateAdvantage(p1Name, p2Name, "Total Wickets", bowl.getTotalWickets1(), bowl.getTotalWickets2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "wicketsPerInnings", evaluateAdvantage(p1Name, p2Name, "Wickets Per Innings", bowl.getWicketsPerInnings1(), bowl.getWicketsPerInnings2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "economyRate", evaluateAdvantage(p1Name, p2Name, "Economy Rate", bowl.getEconomyRate1(), bowl.getEconomyRate2(), "LOWER_IS_BETTER"));
            putAdvantage(legacy, detailed, "bowlingAverage", evaluateAdvantage(p1Name, p2Name, "Bowling Average", bowl.getBowlingAverage1(), bowl.getBowlingAverage2(), "LOWER_IS_BETTER"));
            putAdvantage(legacy, detailed, "bowlingStrikeRate", evaluateAdvantage(p1Name, p2Name, "Bowling Strike Rate", bowl.getBowlingStrikeRate1(), bowl.getBowlingStrikeRate2(), "LOWER_IS_BETTER"));
            putAdvantage(legacy, detailed, "fourWickets", evaluateAdvantage(p1Name, p2Name, "4-Wicket Hauls", bowl.getFourWickets1(), bowl.getFourWickets2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "fiveWickets", evaluateAdvantage(p1Name, p2Name, "5-Wicket Hauls", bowl.getFiveWickets1(), bowl.getFiveWickets2(), "HIGHER_IS_BETTER"));
            putAdvantage(legacy, detailed, "maidens", evaluateAdvantage(p1Name, p2Name, "Maidens", null, null, "HIGHER_IS_BETTER"));
        }

        return new AdvantageCalculationResult(legacy, detailed);
    }

    private static void putAdvantage(
            Map<String, String> legacy,
            Map<String, PlayerComparisonResponse.MetricAdvantageDetail> detailed,
            String key,
            PlayerComparisonResponse.MetricAdvantageDetail detail) {
        detailed.put(key, detail);
        legacy.put(key, detail.getAdvantage());
    }

    private static PlayerComparisonResponse.MetricAdvantageDetail evaluateAdvantage(
            String p1Name, String p2Name, String metricLabel, Number v1, Number v2, String criterion) {

        if (v1 == null || v2 == null) {
            return PlayerComparisonResponse.MetricAdvantageDetail.builder()
                    .advantage("na")
                    .value1(v1 != null ? round2(v1.doubleValue()) : null)
                    .value2(v2 != null ? round2(v2.doubleValue()) : null)
                    .difference(null)
                    .percentageDifference(null)
                    .criterion(criterion)
                    .interpretation("Insufficient data or unrecorded metric for comparison.")
                    .build();
        }

        double d1 = v1.doubleValue();
        double d2 = v2.doubleValue();
        double diff = round2(d1 - d2);

        Double pctDiff = null;
        double minVal = Math.min(Math.abs(d1), Math.abs(d2));
        if (minVal > 0.0001) {
            pctDiff = round2(Math.abs(d1 - d2) / minVal * 100.0);
        }

        String adv;
        String interp;
        if (Math.abs(d1 - d2) < 0.001) {
            adv = "tie";
            interp = String.format("Both %s and %s are level at %.2f in %s.", p1Name, p2Name, d1, metricLabel);
        } else if ("LOWER_IS_BETTER".equals(criterion)) {
            if (d1 <= 0.0 && d2 <= 0.0) {
                adv = "na";
                interp = "Invalid rate (no wickets or overs recorded).";
            } else if (d1 <= 0.0) {
                adv = "player2";
                interp = String.format("%s holds the valid bowling rate advantage (%.2f vs unrecorded/zero).", p2Name, d2);
            } else if (d2 <= 0.0) {
                adv = "player1";
                interp = String.format("%s holds the valid bowling rate advantage (%.2f vs unrecorded/zero).", p1Name, d1);
            } else if (d1 < d2) {
                adv = "player1";
                interp = String.format("%s leads by %.2f%s (lower %s is more efficient).", p1Name, Math.abs(diff), pctDiff != null ? " (-" + pctDiff + "%)" : "", metricLabel);
            } else {
                adv = "player2";
                interp = String.format("%s leads by %.2f%s (lower %s is more efficient).", p2Name, Math.abs(diff), pctDiff != null ? " (-" + pctDiff + "%)" : "", metricLabel);
            }
        } else if ("LOWER_VARIABILITY".equals(criterion)) {
            if (d1 < d2) {
                adv = "player1";
                interp = String.format("%s shows lower relative scoring variance (CV %.1f%% vs %.1f%%), indicating greater consistency across innings.", p1Name, d1, d2);
            } else {
                adv = "player2";
                interp = String.format("%s shows lower relative scoring variance (CV %.1f%% vs %.1f%%), indicating greater consistency across innings.", p2Name, d2, d1);
            }
        } else {
            if (d1 > d2) {
                adv = "player1";
                interp = String.format("%s leads with %.2f vs %.2f%s in %s.", p1Name, d1, d2, pctDiff != null ? " (+" + pctDiff + "%)" : "", metricLabel);
            } else {
                adv = "player2";
                interp = String.format("%s leads with %.2f vs %.2f%s in %s.", p2Name, d2, d1, pctDiff != null ? " (+" + pctDiff + "%)" : "", metricLabel);
            }
        }

        return PlayerComparisonResponse.MetricAdvantageDetail.builder()
                .advantage(adv)
                .value1(round2(d1))
                .value2(round2(d2))
                .difference(diff)
                .percentageDifference(pctDiff)
                .criterion(criterion)
                .interpretation(interp)
                .build();
    }

    private static Double computeSampleStdDev(List<Double> values) {
        if (values == null || values.size() < 2) return null;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sumSq = 0.0;
        for (double v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return round2(Math.sqrt(sumSq / (values.size() - 1)));
    }

    private record TrendResult(String direction, Double slope) {}

    private static TrendResult computeTrend(List<Double> values, double threshold) {
        if (values == null || values.size() < 3) {
            return new TrendResult("INSUFFICIENT_DATA", null);
        }
        int n = values.size();
        double xMean = (n + 1.0) / 2.0;
        double yMean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < n; i++) {
            double x = i + 1;
            double y = values.get(i);
            numerator += (x - xMean) * (y - yMean);
            denominator += Math.pow(x - xMean, 2);
        }
        if (denominator == 0.0) return new TrendResult("STABLE", 0.0);
        double slope = round2(numerator / denominator);
        String dir;
        if (slope > threshold) {
            dir = "UPWARD";
        } else if (slope < -threshold) {
            dir = "DOWNWARD";
        } else {
            dir = "STABLE";
        }
        return new TrendResult(dir, slope);
    }

    private String generateEvidenceBasedInterpretation(
            String p1Name, String p2Name, String role1, String role2, String format, String scope,
            PlayerComparisonResponse.BattingComparisonSummary bat,
            PlayerComparisonResponse.BowlingComparisonSummary bowl,
            PlayerComparisonResponse.RecentFormSummary recent,
            Map<String, PlayerComparisonResponse.MetricAdvantageDetail> detailed) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Head-to-head comparison between %s (%s) and %s (%s) in %s format: ",
                p1Name, role1, p2Name, role2, format));

        if ("MIXED_DISCIPLINES".equals(scope)) {
            sb.append(String.format("Because %s is recognized as a %s and %s as a %s, cross-discipline evaluation is segmented into their respective specialisms to avoid comparing unlike metrics. ",
                    p1Name, role1.toLowerCase(), p2Name, role2.toLowerCase()));
        }

        if (bat != null && (bat.getInnings1() > 0 || bat.getInnings2() > 0)) {
            if (bat.getInnings1() > 0 && bat.getInnings2() > 0) {
                String batRunLead = bat.getTotalRuns1() >= bat.getTotalRuns2() ? p1Name : p2Name;
                sb.append(String.format("In batting, %s has accumulated more total runs (%d vs %d across %d and %d innings). ",
                        batRunLead,
                        Math.max(bat.getTotalRuns1(), bat.getTotalRuns2()),
                        Math.min(bat.getTotalRuns1(), bat.getTotalRuns2()),
                        batRunLead.equals(p1Name) ? bat.getInnings1() : bat.getInnings2(),
                        batRunLead.equals(p1Name) ? bat.getInnings2() : bat.getInnings1()));

                if (bat.getStrikeRate1() != null && bat.getStrikeRate2() != null) {
                    String batSrLead = bat.getStrikeRate1() >= bat.getStrikeRate2() ? p1Name : p2Name;
                    double srLeadVal = batSrLead.equals(p1Name) ? bat.getStrikeRate1() : bat.getStrikeRate2();
                    double srTrailVal = batSrLead.equals(p1Name) ? bat.getStrikeRate2() : bat.getStrikeRate1();
                    sb.append(String.format("%s holds the scoring rate edge with a strike rate of %.2f compared to %.2f. ",
                            batSrLead, srLeadVal, srTrailVal));
                }

                if (bat.getBattingAverage1() != null && bat.getBattingAverage2() != null) {
                    String avgLead = bat.getBattingAverage1() >= bat.getBattingAverage2() ? p1Name : p2Name;
                    sb.append(String.format("Official batting average favors %s (%.2f vs %.2f per dismissal). ",
                            avgLead,
                            Math.max(bat.getBattingAverage1(), bat.getBattingAverage2()),
                            Math.min(bat.getBattingAverage1(), bat.getBattingAverage2())));
                }

                if (bat.getCoefficientOfVariation1() != null && bat.getCoefficientOfVariation2() != null) {
                    String cvLead = bat.getCoefficientOfVariation1() <= bat.getCoefficientOfVariation2() ? p1Name : p2Name;
                    sb.append(String.format("In consistency, %s exhibits lower relative variance in scoring (CV %.1f%% vs %.1f%%). ",
                            cvLead,
                            Math.min(bat.getCoefficientOfVariation1(), bat.getCoefficientOfVariation2()),
                            Math.max(bat.getCoefficientOfVariation1(), bat.getCoefficientOfVariation2())));
                }
            }
        }

        if (bowl != null && (bowl.getInnings1() > 0 || bowl.getInnings2() > 0)) {
            if (bowl.getInnings1() > 0 && bowl.getInnings2() > 0) {
                String wktLead = bowl.getTotalWickets1() >= bowl.getTotalWickets2() ? p1Name : p2Name;
                sb.append(String.format("In bowling, %s leads in wicket volume with %d wickets compared to %d. ",
                        wktLead,
                        Math.max(bowl.getTotalWickets1(), bowl.getTotalWickets2()),
                        Math.min(bowl.getTotalWickets1(), bowl.getTotalWickets2())));

                if (bowl.getEconomyRate1() != null && bowl.getEconomyRate2() != null) {
                    String econLead = bowl.getEconomyRate1() <= bowl.getEconomyRate2() ? p1Name : p2Name;
                    sb.append(String.format("Economy rate favors %s at %.2f runs/over vs %.2f. ",
                            econLead,
                            Math.min(bowl.getEconomyRate1(), bowl.getEconomyRate2()),
                            Math.max(bowl.getEconomyRate1(), bowl.getEconomyRate2())));
                }

                if (bowl.getBowlingStrikeRate1() != null && bowl.getBowlingStrikeRate2() != null) {
                    String bsrLead = bowl.getBowlingStrikeRate1() <= bowl.getBowlingStrikeRate2() ? p1Name : p2Name;
                    sb.append(String.format("Strike rate indicates %s strikes more frequently (every %.1f balls vs %.1f balls per wicket). ",
                            bsrLead,
                            Math.min(bowl.getBowlingStrikeRate1(), bowl.getBowlingStrikeRate2()),
                            Math.max(bowl.getBowlingStrikeRate1(), bowl.getBowlingStrikeRate2())));
                }
            }
        }

        if (recent != null) {
            if ("UPWARD".equals(recent.getRunTrendDirection1()) || "UPWARD".equals(recent.getRunTrendDirection2())) {
                sb.append("Recent form analysis indicates positive scoring momentum in eligible matches. ");
            }
        }

        sb.append("Note: Figures are derived from available match records in the historical dataset and do not substitute for unrecorded career spans.");
        return sb.toString();
    }

    // ─── Role determination ────────────────────────────────────────────────────

    /**
     * Determines role based on relative volume of batting vs bowling data.
     *
     * Thresholds (tunable):
     *  - BATSMAN:    battingInnings > 0 AND (bowlingInnings == 0 OR ratio >= 4)
     *  - BOWLER:     bowlingInnings > 0 AND (battingInnings == 0 OR ratio <= 0.25)
     *  - ALL_ROUNDER: both disciplines have meaningful data
     *  - UNKNOWN:    no data in either discipline
     */
    private static String determineRole(List<BattingRow> batting, List<BowlingRow> bowling) {
        int bat = batting.size();
        int bowl = bowling.size();
        if (bat == 0 && bowl == 0) return "UNKNOWN";
        if (bat > 0 && bowl == 0) return "BATSMAN";
        if (bowl > 0 && bat == 0) return "BOWLER";

        double ratio = (double) bat / bowl;
        if (ratio >= 4.0) return "BATSMAN";
        if (ratio <= 0.25) return "BOWLER";
        return "ALL_ROUNDER";
    }

    private static boolean isBatterRole(String role) {
        return "BATSMAN".equals(role) || "ALL_ROUNDER".equals(role) || "UNKNOWN".equals(role);
    }

    private static boolean isBowlerRole(String role) {
        return "BOWLER".equals(role) || "ALL_ROUNDER".equals(role);
    }

    // ─── Coverage metadata builder ─────────────────────────────────────────────

    private DataCoverage buildCoverage(
            String playerName, String role, String normFmt, boolean filterAll,
            List<BattingRow> allBatting, List<BowlingRow> allBowling,
            List<BattingRow> batting, List<BowlingRow> bowling,
            List<RecentFormEntry> recentBat, List<BowlingRecentFormEntry> recentBowl) {

        String fmtLabel = filterAll ? FORMAT_ALL : normFmt.toUpperCase();

        // Batting coverage
        String batEarliest     = allBatting.stream().map(BattingRow::date).min(Comparator.naturalOrder()).orElse(null);
        String batLatest       = allBatting.stream().map(BattingRow::date).max(Comparator.naturalOrder()).orElse(null);
        String batFmtEarliest  = batting.stream().map(BattingRow::date).min(Comparator.naturalOrder()).orElse(null);
        String batFmtLatest    = batting.stream().map(BattingRow::date).max(Comparator.naturalOrder()).orElse(null);
        String recentBatStart  = recentBat.isEmpty() ? null : recentBat.get(0).getDate();

        // Bowling coverage
        String bwlEarliest     = allBowling.stream().map(BowlingRow::date).min(Comparator.naturalOrder()).orElse(null);
        String bwlLatest       = allBowling.stream().map(BowlingRow::date).max(Comparator.naturalOrder()).orElse(null);
        String bwlFmtEarliest  = bowling.stream().map(BowlingRow::date).min(Comparator.naturalOrder()).orElse(null);
        String bwlFmtLatest    = bowling.stream().map(BowlingRow::date).max(Comparator.naturalOrder()).orElse(null);
        String recentBwlStart  = recentBowl.isEmpty() ? null : recentBowl.get(0).getDate();

        String note = "Dataset may not cover the player's complete career. " +
                "Statistics reflect available match records only.";

        return DataCoverage.builder()
                .playerName(playerName)
                .role(role)
                .selectedFormat(fmtLabel)
                .totalBattingInningsAllFormats(allBatting.size())
                .battingInningsForFormat(batting.size())
                .recentBattingFormCount(recentBat.size())
                .battingEarliestDate(filterAll ? batEarliest : batFmtEarliest)
                .battingLatestDate(filterAll ? batLatest : batFmtLatest)
                .recentBattingFormStartDate(recentBatStart)
                .totalBowlingInningsAllFormats(allBowling.size())
                .bowlingInningsForFormat(bowling.size())
                .recentBowlingFormCount(recentBowl.size())
                .bowlingEarliestDate(filterAll ? bwlEarliest : bwlFmtEarliest)
                .bowlingLatestDate(filterAll ? bwlLatest : bwlFmtLatest)
                .recentBowlingFormStartDate(recentBwlStart)
                .coverageNote(note)
                .build();
    }

    // ─── Recent form builders ──────────────────────────────────────────────────

    private List<RecentFormEntry> buildRecentBattingForm(List<BattingRow> batting, int n) {
        List<BattingRow> sorted = batting.stream()
                .sorted(Comparator.comparing(BattingRow::date).reversed())
                .limit(n)
                .sorted(Comparator.comparing(BattingRow::date))
                .toList();
        return sorted.stream().map(r -> RecentFormEntry.builder()
                .date(r.date()).opposition(r.opposition()).venue(r.venue())
                .format(r.format().toUpperCase())
                .runs(r.runs()).ballsFaced(r.ballsFaced())
                .strikeRate(r.strikeRate()).fours(r.fours()).sixes(r.sixes()).notOut(r.notOut())
                .build()).toList();
    }

    private List<BowlingRecentFormEntry> buildRecentBowlingForm(List<BowlingRow> bowling, int n) {
        List<BowlingRow> sorted = bowling.stream()
                .sorted(Comparator.comparing(BowlingRow::date).reversed())
                .limit(n)
                .sorted(Comparator.comparing(BowlingRow::date))
                .toList();
        return sorted.stream().map(r -> {
            int fullOvers = (int) r.overs();
            int balls = (int) Math.round((r.overs() - fullOvers) * 10);
            int validBalls = fullOvers * 6 + balls;
            String figures = r.wickets() + "/" + r.runsConceded();
            String haul = r.wickets() >= 5 ? "5W" : (r.wickets() == 4 ? "4W" : null);
            double decOvers = fullOvers + balls / 6.0;
            Double econ = r.economy() > 0 ? r.economy() : (decOvers > 0 ? round2((double) r.runsConceded() / decOvers) : null);
            return BowlingRecentFormEntry.builder()
                    .date(r.date()).opposition(r.opposition()).venue(r.venue())
                    .format(r.format().toUpperCase())
                    .overs(r.overs())
                    .runsConceded(r.runsConceded())
                    .wickets(r.wickets())
                    .economy(econ)
                    .figures(figures)
                    .haulBadge(haul)
                    .validBalls(validBalls)
                    .build();
        }).toList();
    }

    // ─── Format breakdown ──────────────────────────────────────────────────────

    private List<FormatStats> buildFormatBreakdown(List<BattingRow> batting, List<BowlingRow> bowling) {
        Set<String> formats = new LinkedHashSet<>();
        batting.stream().map(BattingRow::format).forEach(formats::add);
        bowling.stream().map(BowlingRow::format).forEach(formats::add);

        List<FormatStats> result = new ArrayList<>();
        for (String fmt : formats) {
            List<BattingRow> fb = batting.stream().filter(r -> r.format().equals(fmt)).toList();
            List<BowlingRow> bw = bowling.stream().filter(r -> r.format().equals(fmt)).toList();

            // Batting aggregates
            int totalRuns  = fb.stream().mapToInt(BattingRow::runs).sum();
            int totalBalls = fb.stream().mapToInt(BattingRow::ballsFaced).sum();
            int totalFours = fb.stream().mapToInt(BattingRow::fours).sum();
            int totalSixes = fb.stream().mapToInt(BattingRow::sixes).sum();
            int fifties    = (int) fb.stream().filter(r -> r.runs() >= 50 && r.runs() < 100).count();
            int hundreds   = (int) fb.stream().filter(r -> r.runs() >= 100).count();
            double rpi     = fb.isEmpty() ? 0.0 : round2((double) totalRuns / fb.size());
            double batSR   = totalBalls == 0 ? 0.0 : round2((double) totalRuns / totalBalls * 100.0);

            // Bowling aggregates
            int totalWickets      = bw.stream().mapToInt(BowlingRow::wickets).sum();
            int totalRunsConceded = bw.stream().mapToInt(BowlingRow::runsConceded).sum();
            double totalDecOvers  = bw.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum();
            double avgEconomy     = bw.isEmpty() ? 0.0
                    : round2(bw.stream().mapToDouble(BowlingRow::economy).average().orElse(0.0));
            // Proper economy rate from totals (not average of averages)
            Double economyRate    = totalDecOvers > 0 ? round2(totalRunsConceded / totalDecOvers) : null;
            Double bowlingAvg     = totalWickets > 0  ? round2((double) totalRunsConceded / totalWickets) : null;

            int totalBallsBowled = (int) bw.stream().mapToDouble(r -> {
                int fullOvers = (int) r.overs();
                double balls = Math.round((r.overs() - fullOvers) * 10);
                return fullOvers * 6 + balls;
            }).sum();
            Double bowlingSR = totalWickets > 0 ? round2((double) totalBallsBowled / totalWickets) : null;
            int fourW = (int) bw.stream().filter(r -> r.wickets() == 4).count();
            int fiveW = (int) bw.stream().filter(r -> r.wickets() >= 5).count();

            result.add(FormatStats.builder()
                    .format(fmt.toUpperCase())
                    .battingInnings(fb.size()).totalRuns(totalRuns)
                    .runsPerInnings(rpi).battingStrikeRate(batSR)
                    .fifties(fifties).hundreds(hundreds)
                    .totalFours(totalFours).totalSixes(totalSixes)
                    .bowlingInnings(bw.size()).totalWickets(totalWickets)
                    .totalRunsConceded(totalRunsConceded).totalDecimalOvers(round2(totalDecOvers))
                    .bowlingAverage(bowlingAvg).economyRate(economyRate)
                    .economy(avgEconomy)       // legacy
                    .runsPerWicket(bowlingAvg) // legacy alias
                    .bowlingStrikeRate(bowlingSR)
                    .fourWickets(fourW)
                    .fiveWickets(fiveW)
                    .build());
        }
        result.sort(Comparator.comparingInt(fs -> formatOrder(fs.getFormat())));
        return result;
    }

    // ─── Batting venue / opposition breakdowns ────────────────────────────────

    private List<VenueStats> buildBattingVenueBreakdown(List<BattingRow> batting) {
        Map<String, List<BattingRow>> grouped = batting.stream()
                .collect(Collectors.groupingBy(BattingRow::venue));
        return grouped.entrySet().stream()
                .map(e -> {
                    List<BattingRow> rows = e.getValue();
                    int totalRuns  = rows.stream().mapToInt(BattingRow::runs).sum();
                    int totalBalls = rows.stream().mapToInt(BattingRow::ballsFaced).sum();
                    double rpi = round2((double) totalRuns / rows.size());
                    double sr  = totalBalls == 0 ? 0.0 : round2((double) totalRuns / totalBalls * 100.0);
                    int fifties  = (int) rows.stream().filter(r -> r.runs() >= 50 && r.runs() < 100).count();
                    int hundreds = (int) rows.stream().filter(r -> r.runs() >= 100).count();
                    return VenueStats.builder()
                            .venue(e.getKey()).innings(rows.size()).totalRuns(totalRuns)
                            .runsPerInnings(rpi).strikeRate(sr).fifties(fifties).hundreds(hundreds)
                            .build();
                })
                .sorted(Comparator.comparingInt(VenueStats::getInnings).reversed())
                .limit(10).toList();
    }

    private List<OppositionStats> buildBattingOppositionBreakdown(List<BattingRow> batting) {
        Map<String, List<BattingRow>> grouped = batting.stream()
                .collect(Collectors.groupingBy(BattingRow::opposition));
        return grouped.entrySet().stream()
                .map(e -> {
                    List<BattingRow> rows = e.getValue();
                    int totalRuns  = rows.stream().mapToInt(BattingRow::runs).sum();
                    int totalBalls = rows.stream().mapToInt(BattingRow::ballsFaced).sum();
                    double rpi = round2((double) totalRuns / rows.size());
                    double sr  = totalBalls == 0 ? 0.0 : round2((double) totalRuns / totalBalls * 100.0);
                    int fifties  = (int) rows.stream().filter(r -> r.runs() >= 50 && r.runs() < 100).count();
                    int hundreds = (int) rows.stream().filter(r -> r.runs() >= 100).count();
                    return OppositionStats.builder()
                            .opposition(e.getKey()).innings(rows.size()).totalRuns(totalRuns)
                            .runsPerInnings(rpi).strikeRate(sr).fifties(fifties).hundreds(hundreds)
                            .build();
                })
                .sorted(Comparator.comparingInt(OppositionStats::getInnings).reversed())
                .toList();
    }

    // ─── Bowling venue / opposition breakdowns ────────────────────────────────

    private List<BowlingVenueStats> buildBowlingVenueBreakdown(List<BowlingRow> bowling) {
        Map<String, List<BowlingRow>> grouped = bowling.stream()
                .collect(Collectors.groupingBy(BowlingRow::venue));
        return grouped.entrySet().stream()
                .map(e -> {
                    List<BowlingRow> rows = e.getValue();
                    int totalWickets      = rows.stream().mapToInt(BowlingRow::wickets).sum();
                    int totalRunsConceded = rows.stream().mapToInt(BowlingRow::runsConceded).sum();
                    double totalDecOvers  = rows.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum();
                    Double bowlAvg  = totalWickets > 0  ? round2((double) totalRunsConceded / totalWickets) : null;
                    Double econRate = totalDecOvers > 0 ? round2(totalRunsConceded / totalDecOvers) : null;
                    return BowlingVenueStats.builder()
                            .venue(e.getKey()).appearances(rows.size())
                            .totalWickets(totalWickets).totalRunsConceded(totalRunsConceded)
                            .totalOvers(round2(totalDecOvers))
                            .bowlingAverage(bowlAvg).economyRate(econRate)
                            .build();
                })
                .sorted(Comparator.comparingInt(BowlingVenueStats::getAppearances).reversed())
                .limit(10).toList();
    }

    private List<BowlingOppositionStats> buildBowlingOppositionBreakdown(List<BowlingRow> bowling) {
        Map<String, List<BowlingRow>> grouped = bowling.stream()
                .collect(Collectors.groupingBy(BowlingRow::opposition));
        return grouped.entrySet().stream()
                .map(e -> {
                    List<BowlingRow> rows = e.getValue();
                    int totalWickets      = rows.stream().mapToInt(BowlingRow::wickets).sum();
                    int totalRunsConceded = rows.stream().mapToInt(BowlingRow::runsConceded).sum();
                    double totalDecOvers  = rows.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum();
                    Double bowlAvg  = totalWickets > 0  ? round2((double) totalRunsConceded / totalWickets) : null;
                    Double econRate = totalDecOvers > 0 ? round2(totalRunsConceded / totalDecOvers) : null;
                    return BowlingOppositionStats.builder()
                            .opposition(e.getKey()).appearances(rows.size())
                            .totalWickets(totalWickets).totalRunsConceded(totalRunsConceded)
                            .totalOvers(round2(totalDecOvers))
                            .bowlingAverage(bowlAvg).economyRate(econRate)
                            .build();
                })
                .sorted(Comparator.comparingInt(BowlingOppositionStats::getAppearances).reversed())
                .toList();
    }

    // ─── Single-Player Role-Based Analytics Builders ──────────────────────────

    private BattingAnalyticsSummary buildSinglePlayerBattingSummary(List<BattingRow> b) {
        if (b == null || b.isEmpty()) return null;
        int inn = b.size();
        int totalRuns = b.stream().mapToInt(BattingRow::runs).sum();
        int balls = b.stream().mapToInt(BattingRow::ballsFaced).sum();
        int notOuts = (int) b.stream().filter(BattingRow::notOut).count();
        int dismissals = inn - notOuts;

        Double rpi = inn > 0 ? round2((double) totalRuns / inn) : null;
        Double avg = dismissals > 0 ? round2((double) totalRuns / dismissals) : null;
        Double sr = balls > 0 ? round2((double) totalRuns / balls * 100.0) : null;

        int fours = b.stream().mapToInt(BattingRow::fours).sum();
        int sixes = b.stream().mapToInt(BattingRow::sixes).sum();
        int boundaryRuns = 4 * fours + 6 * sixes;
        Double bPct = totalRuns > 0 ? round2((double) boundaryRuns / totalRuns * 100.0) : null;
        Double bBallPct = balls > 0 ? round2((double) (fours + sixes) / balls * 100.0) : null;

        int fifties = (int) b.stream().filter(r -> r.runs() >= 50 && r.runs() < 100).count();
        int hundreds = (int) b.stream().filter(r -> r.runs() >= 100).count();

        Double stdDev = computeSampleStdDev(b.stream().map(r -> (double) r.runs()).toList());
        Double cv = (stdDev != null && rpi != null && rpi > 0 && inn >= 3)
                ? round2((stdDev / rpi) * 100.0)
                : null;

        BattingRow hsRow = b.stream().max(Comparator.comparingInt(BattingRow::runs)).orElse(null);
        Integer hs = hsRow != null ? hsRow.runs() : null;
        Boolean hsNotOut = hsRow != null ? hsRow.notOut() : null;

        String warning = inn < 3 ? "Sample size is fewer than 3 innings; statistical reliability is limited." : null;
        String note = "Consistency is evaluated via sample standard deviation (σ) and coefficient of variation (CV% = σ / mean * 100) of runs per innings. Lower CV indicates tighter scoring dispersion. Requires >= 3 innings.";

        return BattingAnalyticsSummary.builder()
                .innings(inn)
                .totalRuns(totalRuns)
                .ballsFaced(balls)
                .notOuts(notOuts)
                .dismissals(dismissals)
                .highestScore(hs)
                .highestScoreNotOut(hsNotOut)
                .runsPerInnings(rpi)
                .battingAverage(avg)
                .strikeRate(sr)
                .fours(fours)
                .sixes(sixes)
                .boundaryRuns(boundaryRuns)
                .boundaryPercentage(bPct)
                .boundaryBallPercentage(bBallPct)
                .fifties(fifties)
                .hundreds(hundreds)
                .stdDevRuns(stdDev)
                .coefficientOfVariation(cv)
                .consistencyNote(note)
                .sampleSizeWarning(warning)
                .build();
    }

    private BowlingAnalyticsSummary buildSinglePlayerBowlingSummary(List<BowlingRow> bw) {
        if (bw == null || bw.isEmpty()) return null;
        int inn = bw.size();
        double decOvers = round2(bw.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum());
        int balls = bw.stream().mapToInt(r -> {
            int fullOvers = (int) r.overs();
            int b = (int) Math.round((r.overs() - fullOvers) * 10);
            return fullOvers * 6 + b;
        }).sum();

        int rc = bw.stream().mapToInt(BowlingRow::runsConceded).sum();
        int w = bw.stream().mapToInt(BowlingRow::wickets).sum();

        Double wpi = inn > 0 ? round2((double) w / inn) : null;
        Double econ = decOvers > 0 ? round2((double) rc / decOvers) : null;
        Double avg = w > 0 ? round2((double) rc / w) : null;
        Double sr = w > 0 ? round2((double) balls / w) : null;

        int fourW = (int) bw.stream().filter(r -> r.wickets() == 4).count();
        int fiveW = (int) bw.stream().filter(r -> r.wickets() >= 5).count();
        Double stdDevW = computeSampleStdDev(bw.stream().map(r -> (double) r.wickets()).toList());

        String warning = inn < 3 ? "Sample size is fewer than 3 bowling spells; statistical reliability is limited." : null;
        String note = "Bowling consistency is measured by standard deviation of wickets per spell. Quality is best interpreted alongside economy rate and overs bowled.";

        return BowlingAnalyticsSummary.builder()
                .innings(inn)
                .totalOvers(decOvers)
                .validBallsBowled(balls)
                .runsConceded(rc)
                .totalWickets(w)
                .wicketsPerInnings(wpi)
                .economyRate(econ)
                .bowlingAverage(avg)
                .bowlingStrikeRate(sr)
                .fourWickets(fourW)
                .fiveWickets(fiveW)
                .stdDevWickets(stdDevW)
                .consistencyNote(note)
                .sampleSizeWarning(warning)
                .build();
    }

    private ScoreDistribution buildScoreDistribution(List<BattingRow> b) {
        if (b == null || b.isEmpty()) return null;
        int total = b.size();
        int singleDigits = (int) b.stream().filter(r -> r.runs() < 10).count();
        int starts = (int) b.stream().filter(r -> r.runs() >= 10 && r.runs() < 30).count();
        int solidStarts = (int) b.stream().filter(r -> r.runs() >= 30 && r.runs() < 50).count();
        int fifties = (int) b.stream().filter(r -> r.runs() >= 50 && r.runs() < 100).count();
        int centuries = (int) b.stream().filter(r -> r.runs() >= 100).count();

        double sdPct = round2((double) singleDigits / total * 100.0);
        double sPct = round2((double) starts / total * 100.0);
        double ssPct = round2((double) solidStarts / total * 100.0);
        double fPct = round2((double) fifties / total * 100.0);
        double cPct = round2((double) centuries / total * 100.0);

        String interp;
        if (centuries + fifties >= Math.max(1, (int) Math.round(0.25 * total))) {
            interp = String.format("High milestone conversion: builds substantial innings (50+) in %.1f%% of batted appearances (%d fifties, %d centuries).",
                    round2(fPct + cPct), fifties, centuries);
        } else if (starts + solidStarts >= Math.max(1, (int) Math.round(0.40 * total))) {
            interp = String.format("Consistent start accumulator: settles past initial overs (10-49 runs) in %.1f%% of innings.",
                    round2(sPct + ssPct));
        } else if (singleDigits >= Math.max(1, (int) Math.round(0.40 * total))) {
            interp = String.format("Aggressive/high-risk profile: early dismissals (<10 runs) occur in %.1f%% of appearances, offset by peak scores.",
                    sdPct);
        } else {
            interp = String.format("Balanced scoring spread across %d innings: %d single digits, %d starts (10-29), %d solid starts (30-49), and %d 50+ scores.",
                    total, singleDigits, starts, solidStarts, fifties + centuries);
        }

        return ScoreDistribution.builder()
                .singleDigits(singleDigits)
                .starts(starts)
                .solidStarts(solidStarts)
                .fifties(fifties)
                .centuries(centuries)
                .singleDigitsPct(sdPct)
                .startsPct(sPct)
                .solidStartsPct(ssPct)
                .fiftiesPct(fPct)
                .centuriesPct(cPct)
                .patternInterpretation(interp)
                .build();
    }

    private BowlingDistribution buildBowlingDistribution(List<BowlingRow> bw) {
        if (bw == null || bw.isEmpty()) return null;
        int total = bw.size();
        int zeroW = (int) bw.stream().filter(r -> r.wickets() == 0).count();
        int oneW = (int) bw.stream().filter(r -> r.wickets() == 1).count();
        int twoW = (int) bw.stream().filter(r -> r.wickets() == 2).count();
        int threeW = (int) bw.stream().filter(r -> r.wickets() == 3).count();
        int fourPlusW = (int) bw.stream().filter(r -> r.wickets() >= 4).count();

        double zwPct = round2((double) zeroW / total * 100.0);
        double owPct = round2((double) oneW / total * 100.0);
        double twPct = round2((double) twoW / total * 100.0);
        double thPct = round2((double) threeW / total * 100.0);
        double fpPct = round2((double) fourPlusW / total * 100.0);

        String interp;
        if (twoW + threeW + fourPlusW >= Math.max(1, (int) Math.round(0.35 * total))) {
            interp = String.format("Multi-wicket threat: takes 2+ wickets in %.1f%% of bowling spells, including %d match-defining hauls (4W+).",
                    round2(twPct + thPct + fpPct), fourPlusW);
        } else if (fourPlusW >= 1 && fpPct >= 10.0) {
            interp = String.format("High-impact match winner: produces 4+ wicket hauls in %.1f%% of spells.", fpPct);
        } else if (zeroW >= Math.max(1, (int) Math.round(0.50 * total))) {
            interp = String.format("Containing/support bowler: wicketless in %.1f%% of appearances; effectiveness is best evaluated through economy rate.", zwPct);
        } else {
            interp = String.format("Steady wicket-taking pattern: claims at least 1 wicket in %.1f%% of bowling spells (%d spells total).",
                    round2(100.0 - zwPct), total);
        }

        return BowlingDistribution.builder()
                .zeroWickets(zeroW)
                .oneWicket(oneW)
                .twoWickets(twoW)
                .threeWickets(threeW)
                .fourPlusWickets(fourPlusW)
                .zeroWicketsPct(zwPct)
                .oneWicketPct(owPct)
                .twoWicketsPct(twPct)
                .threeWicketsPct(thPct)
                .fourPlusWicketsPct(fpPct)
                .patternInterpretation(interp)
                .build();
    }

    private SinglePlayerRecentFormSummary buildSinglePlayerRecentFormSummary(
            List<BattingRow> batting, List<BowlingRow> bowling, int recentN, String role) {

        int safeN = Math.max(1, recentN);
        List<BattingRow> recBat = getChronologicalRecent(batting, Comparator.comparing(BattingRow::date), safeN);
        List<BowlingRow> recBowl = getChronologicalRecent(bowling, Comparator.comparing(BowlingRow::date), safeN);

        int batInn = recBat.size();
        int totalRuns = recBat.stream().mapToInt(BattingRow::runs).sum();
        int totalBalls = recBat.stream().mapToInt(BattingRow::ballsFaced).sum();
        Double meanR = batInn > 0 ? round2((double) totalRuns / batInn) : null;
        Double stdDevR = computeSampleStdDev(recBat.stream().map(r -> (double) r.runs()).toList());
        Double cvR = (stdDevR != null && meanR != null && meanR > 0 && batInn >= 3)
                ? round2((stdDevR / meanR) * 100.0)
                : null;
        Integer highR = recBat.isEmpty() ? null : recBat.stream().mapToInt(BattingRow::runs).max().orElse(0);
        Integer lowR = recBat.isEmpty() ? null : recBat.stream().mapToInt(BattingRow::runs).min().orElse(0);
        Double winBatSR = totalBalls > 0 ? round2((double) totalRuns / totalBalls * 100.0) : null;
        TrendResult runTrend = computeTrend(recBat.stream().map(r -> (double) r.runs()).toList(), 1.0);
        String batWarn = batInn < 3 ? "Window has fewer than 3 eligible batting innings. Trends carry limited statistical confidence." : null;

        int bowlInn = recBowl.size();
        int totalW = recBowl.stream().mapToInt(BowlingRow::wickets).sum();
        int totalRc = recBowl.stream().mapToInt(BowlingRow::runsConceded).sum();
        double totalDecOvers = round2(recBowl.stream().mapToDouble(r -> csvOversToDecimal(r.overs())).sum());
        int totalValidBalls = recBowl.stream().mapToInt(r -> {
            int fo = (int) r.overs();
            int b = (int) Math.round((r.overs() - fo) * 10);
            return fo * 6 + b;
        }).sum();

        Double meanW = bowlInn > 0 ? round2((double) totalW / bowlInn) : null;
        Double econ = totalDecOvers > 0 ? round2((double) totalRc / totalDecOvers) : null;
        Double avg = totalW > 0 ? round2((double) totalRc / totalW) : null;
        Double sr = totalW > 0 ? round2((double) totalValidBalls / totalW) : null;
        int fourW = (int) recBowl.stream().filter(r -> r.wickets() == 4).count();
        int fiveW = (int) recBowl.stream().filter(r -> r.wickets() >= 5).count();
        Double stdDevW = computeSampleStdDev(recBowl.stream().map(r -> (double) r.wickets()).toList());
        Integer highW = recBowl.isEmpty() ? null : recBowl.stream().mapToInt(BowlingRow::wickets).max().orElse(0);
        Integer lowW = recBowl.isEmpty() ? null : recBowl.stream().mapToInt(BowlingRow::wickets).min().orElse(0);
        TrendResult wktTrend = computeTrend(recBowl.stream().map(r -> (double) r.wickets()).toList(), 0.15);
        String bowlWarn = bowlInn < 3 ? "Window has fewer than 3 eligible bowling spells. Trends carry limited statistical confidence." : null;

        String discipline = "ALL_ROUNDER".equalsIgnoreCase(role) ? "BOTH"
                : (isBowlerRole(role) ? "BOWLING" : "BATTING");

        String basis = String.format("Calculated across the most recent %d eligible appearances in chronological order. Missing matches are not zero-filled.", safeN);

        return SinglePlayerRecentFormSummary.builder()
                .recentN(safeN)
                .focusDiscipline(discipline)
                .calculationBasis(basis)
                .battingInnings(batInn)
                .totalRuns(totalRuns)
                .meanRuns(meanR)
                .stdDevRuns(stdDevR)
                .coefficientOfVariation(cvR)
                .highestRuns(highR)
                .lowestRuns(lowR)
                .windowBattingStrikeRate(winBatSR)
                .runTrendDirection(runTrend.direction())
                .runTrendSlope(runTrend.slope())
                .sampleSizeWarning(batWarn)
                .bowlingInnings(bowlInn)
                .totalWickets(totalW)
                .totalRunsConceded(totalRc)
                .totalOvers(totalDecOvers)
                .totalValidBalls(totalValidBalls)
                .meanWickets(meanW)
                .recentEconomyRate(econ)
                .recentBowlingAverage(avg)
                .recentBowlingStrikeRate(sr)
                .fourWickets(fourW)
                .fiveWickets(fiveW)
                .stdDevWickets(stdDevW)
                .highestWickets(highW)
                .lowestWickets(lowW)
                .wicketTrendDirection(wktTrend.direction())
                .wicketTrendSlope(wktTrend.slope())
                .bowlingSampleSizeWarning(bowlWarn)
                .build();
    }

    // ─── Player name resolution (unchanged from previous) ─────────────────────

    public String resolvePlayerName(String rawQuery) {
        if (rawQuery == null) return null;
        String query = normalize(rawQuery);
        if (query.isEmpty()) return null;
        List<ParsedPlayer> candidates = new ArrayList<>(canonicalPlayerMap.values());

        // a. exact normalized name
        List<ParsedPlayer> matchesA = candidates.stream().filter(p -> p.normalizedName().equals(query)).toList();
        if (!matchesA.isEmpty()) return returnSingleOrThrow(rawQuery, matchesA);

        String[] qTokens = query.split(" ");

        // b. exact surname
        List<ParsedPlayer> matchesB = candidates.stream().filter(p -> p.surnameCandidates().contains(query)).toList();
        if (!matchesB.isEmpty()) return returnSingleOrThrow(rawQuery, matchesB);

        // c. full-name to initial(s) + surname
        if (qTokens.length >= 2) {
            List<String> qSurnames = new ArrayList<>();
            qSurnames.add(qTokens[qTokens.length - 1]);
            if (qTokens.length >= 3 && isCompoundParticle(qTokens[qTokens.length - 2]))
                qSurnames.add(qTokens[qTokens.length - 2] + " " + qTokens[qTokens.length - 1]);
            if (qTokens.length >= 4 && isCompoundParticle(qTokens[qTokens.length - 3]) && isCompoundParticle(qTokens[qTokens.length - 2]))
                qSurnames.add(qTokens[qTokens.length - 3] + " " + qTokens[qTokens.length - 2] + " " + qTokens[qTokens.length - 1]);
            String longestQSur = qSurnames.stream().max(Comparator.comparingInt(String::length)).orElse(qTokens[qTokens.length - 1]);
            int numQSurTokens  = longestQSur.split(" ").length;
            int numQForeTokens = Math.max(0, qTokens.length - numQSurTokens);
            List<Character> qInitials = new ArrayList<>();
            for (int i = 0; i < numQForeTokens; i++) {
                String ft = qTokens[i];
                if (!ft.isEmpty() && Character.isLetter(ft.charAt(0))) qInitials.add(ft.charAt(0));
            }
            List<ParsedPlayer> matchesC = candidates.stream()
                    .filter(p -> {
                        boolean surnameMatches = p.surnameCandidates().stream().anyMatch(qSurnames::contains);
                        if (!surnameMatches) return false;
                        if (p.initials().isEmpty() || qInitials.isEmpty()) return true;
                        boolean initialMatch = p.initials().get(0).equals(qInitials.get(0))
                                || p.initials().contains(qInitials.get(0));
                        if (!initialMatch) return false;
                        if (qInitials.size() > 1 && p.initials().size() > 1) {
                            int checkCount = Math.min(qInitials.size(), p.initials().size());
                            for (int i = 0; i < checkCount; i++) {
                                if (!p.initials().get(i).equals(qInitials.get(i))) return false;
                            }
                        }
                        return true;
                    }).toList();
            if (!matchesC.isEmpty()) return returnSingleOrThrow(rawQuery, matchesC);
        }

        // d. token-based matching
        if (qTokens.length >= 2) {
            List<ParsedPlayer> matchesD = candidates.stream()
                    .filter(p -> {
                        Set<Integer> matchedPlayerIndices = new HashSet<>();
                        for (String qt : qTokens) {
                            int foundIdx = -1;
                            for (int i = 0; i < p.tokens().size(); i++) {
                                if (matchedPlayerIndices.contains(i)) continue;
                                String pt = p.tokens().get(i);
                                if (pt.equals(qt)
                                        || (qt.length() == 1 && pt.startsWith(qt))
                                        || (pt.length() == 1 && qt.startsWith(pt))) {
                                    foundIdx = i; break;
                                }
                            }
                            if (foundIdx == -1) return false;
                            matchedPlayerIndices.add(foundIdx);
                        }
                        return true;
                    }).toList();
            if (!matchesD.isEmpty()) return returnSingleOrThrow(rawQuery, matchesD);
        }

        // e. substring matching
        List<ParsedPlayer> matchesE = candidates.stream()
                .filter(p -> p.normalizedName().contains(query)).toList();
        if (!matchesE.isEmpty()) return returnSingleOrThrow(rawQuery, matchesE);

        return null;
    }

    private String returnSingleOrThrow(String rawQuery, List<ParsedPlayer> matches) {
        if (matches.size() == 1) return matches.get(0).originalName();
        String names = matches.stream().map(ParsedPlayer::originalName).collect(Collectors.joining(", ", "[", "]"));
        throw new IllegalArgumentException("Multiple players match '" + rawQuery + "': " + names + ". Please specify the exact name.");
    }

    // ─── Normalization & parsing helpers ──────────────────────────────────────

    public static String normalize(String s) {
        if (s == null) return "";
        return s.replace('.', ' ').replace(',', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
    }

    /**
     * Normalises format strings to a consistent lowercase canonical form:
     *  odi → odi, t20i → t20i, test → test, ALL → ALL (uppercase sentinel preserved).
     */
    public static String normalizeFormat(String fmt) {
        if (fmt == null) return FORMAT_ALL;
        String lower = fmt.trim().toLowerCase(Locale.ENGLISH);
        return switch (lower) {
            case "t20", "t20i" -> "t20i";
            case "odi"         -> "odi";
            case "test"        -> "test";
            case "all"         -> FORMAT_ALL;
            default            -> lower;
        };
    }

    /**
     * Converts cricket overs notation to true decimal overs.
     * e.g. 4.3 (4 overs 3 balls) → 4.5, 10.0 → 10.0
     */
    public static double csvOversToDecimal(double csvOvers) {
        int fullOvers = (int) csvOvers;
        double balls  = Math.round((csvOvers - fullOvers) * 10);  // avoid floating-point drift
        return fullOvers + balls / 6.0;
    }

    private static boolean isCompoundParticle(String s) {
        return s.equals("de") || s.equals("van") || s.equals("der") || s.equals("den")
                || s.equals("da") || s.equals("di") || s.equals("al") || s.equals("el")
                || s.equals("du") || s.equals("von");
    }

    private static int safeInt(String v) {
        if (v == null || v.isBlank()) return 0;
        try { return (int) Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static double safeDouble(String v) {
        if (v == null || v.isBlank()) return 0.0;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static int formatOrder(String fmt) {
        return switch (fmt.toUpperCase()) {
            case "TEST" -> 0;
            case "ODI"  -> 1;
            case "T20I" -> 2;
            case "T20"  -> 3;
            default     -> 4;
        };
    }
}
