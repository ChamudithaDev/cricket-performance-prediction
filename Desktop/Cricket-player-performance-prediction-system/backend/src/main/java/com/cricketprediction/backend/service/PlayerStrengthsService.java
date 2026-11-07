
package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.analytics.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class PlayerStrengthsService {

    private final PlayerAnalyticsService analyticsService;

    @Value("${analytics.matchups-batter-csv:../ml-service/data/player_bowler_matchups.csv}")
    private String batterMatchupsCsvPath;

    @Value("${analytics.matchups-bowler-csv:../ml-service/data/bowler_batter_matchups.csv}")
    private String bowlerMatchupsCsvPath;

    @Value("${analytics.phase-csv:../ml-service/data/player_phase_performance.csv}")
    private String phaseCsvPath;

    // Minimum sample size thresholds as specified
    public static final int MIN_BATTER_BALLS = 20;
    public static final int MIN_BOWLER_BALLS = 20;
    public static final int MIN_PHASE_BALLS = 30;

    // In-memory indexed stores
    private final Map<String, List<BatterMatchupCsvRow>> batterMatchupsMap = new HashMap<>();
    private final Map<String, List<BowlerMatchupCsvRow>> bowlerMatchupsMap = new HashMap<>();
    private final Map<String, List<PhaseCsvRow>> phaseMap = new HashMap<>();

    private record BatterMatchupCsvRow(
            String batter, String bowler, String format,
            int matches, int innings, int ballsFaced, int runs,
            int fours, int sixes, double strikeRate, int dismissals,
            int dotBalls, double dotPct, double boundaryPct,
            String firstDate, String lastDate
    ) {}

    private record BowlerMatchupCsvRow(
            String bowler, String batter, String format,
            int matches, int innings, int legalBalls, int runsConceded,
            int wickets, double economy, double bowlingAvg, double bowlingSr,
            int dotBalls, double dotPct, int boundariesConceded,
            String firstDate, String lastDate
    ) {}

    private record PhaseCsvRow(
            String player, String discipline, String format, String phase,
            int matches, int innings, int balls, int runs,
            int fours, int sixes, int wicketsOrDismissals,
            int dotBalls, double rateStat, double avgStat,
            double dotPct, double boundaryPct
    ) {}

    @PostConstruct
    public void loadData() {
        loadBatterMatchups();
        loadBowlerMatchups();
        loadPhasePerformance();
    }

    private File resolveFile(String pathStr) {
        Path path = Paths.get(pathStr);
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path).normalize();
        }
        File f = path.toFile();
        if (!f.exists()) {
            // Also check relative to ml-service/data if run from project root
            File alt = new File("ml-service/data", f.getName());
            if (alt.exists()) return alt;
        }
        return f;
    }

    private void loadBatterMatchups() {
        File file = resolveFile(batterMatchupsCsvPath);
        if (!file.exists()) {
            log.warn("[PlayerStrengths] Batter matchups CSV not found at: {}", file.getAbsolutePath());
            return;
        }
        log.info("[PlayerStrengths] Loading batter matchups from: {}", file.getAbsolutePath());
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line = br.readLine(); // Header
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length < 16) continue;

                BatterMatchupCsvRow row = new BatterMatchupCsvRow(
                        parts[0].trim(), parts[1].trim(), parts[2].trim().toUpperCase(),
                        safeInt(parts[3]), safeInt(parts[4]), safeInt(parts[5]), safeInt(parts[6]),
                        safeInt(parts[7]), safeInt(parts[8]), safeDouble(parts[9]), safeInt(parts[10]),
                        safeInt(parts[11]), safeDouble(parts[12]), safeDouble(parts[13]),
                        parts[14].trim(), parts[15].trim()
                );
                String key = PlayerAnalyticsService.normalize(row.batter);
                batterMatchupsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                count++;
            }
            log.info("[PlayerStrengths] Loaded {} batter matchup rows for {} unique batters.", count, batterMatchupsMap.size());
        } catch (IOException ex) {
            log.error("[PlayerStrengths] Failed to read batter matchups CSV: {}", ex.getMessage());
        }
    }

    private void loadBowlerMatchups() {
        File file = resolveFile(bowlerMatchupsCsvPath);
        if (!file.exists()) {
            log.warn("[PlayerStrengths] Bowler matchups CSV not found at: {}", file.getAbsolutePath());
            return;
        }
        log.info("[PlayerStrengths] Loading bowler matchups from: {}", file.getAbsolutePath());
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length < 16) continue;

                BowlerMatchupCsvRow row = new BowlerMatchupCsvRow(
                        parts[0].trim(), parts[1].trim(), parts[2].trim().toUpperCase(),
                        safeInt(parts[3]), safeInt(parts[4]), safeInt(parts[5]), safeInt(parts[6]),
                        safeInt(parts[7]), safeDouble(parts[8]), safeDouble(parts[9]), safeDouble(parts[10]),
                        safeInt(parts[11]), safeDouble(parts[12]), safeInt(parts[13]),
                        parts[14].trim(), parts[15].trim()
                );
                String key = PlayerAnalyticsService.normalize(row.bowler);
                bowlerMatchupsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                count++;
            }
            log.info("[PlayerStrengths] Loaded {} bowler matchup rows for {} unique bowlers.", count, bowlerMatchupsMap.size());
        } catch (IOException ex) {
            log.error("[PlayerStrengths] Failed to read bowler matchups CSV: {}", ex.getMessage());
        }
    }

    private void loadPhasePerformance() {
        File file = resolveFile(phaseCsvPath);
        if (!file.exists()) {
            log.warn("[PlayerStrengths] Phase performance CSV not found at: {}", file.getAbsolutePath());
            return;
        }
        log.info("[PlayerStrengths] Loading phase performance from: {}", file.getAbsolutePath());
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length < 16) continue;

                PhaseCsvRow row = new PhaseCsvRow(
                        parts[0].trim(), parts[1].trim().toUpperCase(), parts[2].trim().toUpperCase(),
                        parts[3].trim().toUpperCase(), safeInt(parts[4]), safeInt(parts[5]),
                        safeInt(parts[6]), safeInt(parts[7]), safeInt(parts[8]), safeInt(parts[9]),
                        safeInt(parts[10]), safeInt(parts[11]), safeDouble(parts[12]), safeDouble(parts[13]),
                        safeDouble(parts[14]), safeDouble(parts[15])
                );
                String key = PlayerAnalyticsService.normalize(row.player);
                phaseMap.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
                count++;
            }
            log.info("[PlayerStrengths] Loaded {} phase records for {} unique players.", count, phaseMap.size());
        } catch (IOException ex) {
            log.error("[PlayerStrengths] Failed to read phase CSV: {}", ex.getMessage());
        }
    }

    /**
     * Resolves player strengths, matchups, and match-phase metrics for the given player and format.
     */
    public PlayerStrengthsResponse getPlayerStrengths(String playerName, String requestedFormat) {
        String resolvedName = analyticsService.resolvePlayerName(playerName);
        if (resolvedName == null) {
            throw new IllegalArgumentException("No player found matching: '" + playerName + "'");
        }

        String format = (requestedFormat == null || requestedFormat.isBlank()) ? "ALL" : requestedFormat.trim().toUpperCase();
        if ("T20".equals(format)) format = "T20I";

        // Fetch format analytics baseline from PlayerAnalyticsService
        PlayerAnalyticsResponse analytics = analyticsService.getAnalytics(resolvedName, format, 15);
        String detectedRole = analytics != null ? analytics.getRole() : "UNKNOWN";
        Double baselineSr = (analytics != null && analytics.getBattingSummary() != null)
                ? analytics.getBattingSummary().getStrikeRate() : null;
        Double baselineEcon = (analytics != null && analytics.getBowlingSummary() != null)
                ? analytics.getBowlingSummary().getEconomyRate() : null;

        String key = PlayerAnalyticsService.normalize(resolvedName);

        // 1. Process Batter Matchups
        List<BatterVsBowlerDto> batterMatchups = buildBatterMatchups(key, format, baselineSr);

        // 2. Process Bowler Matchups
        List<BowlerVsBatterDto> bowlerMatchups = buildBowlerMatchups(key, format, baselineEcon);

        // 3. Process Phase Performance
        String phaseNotice = null;
        List<PhasePerformanceDto> battingPhases = new ArrayList<>();
        List<PhasePerformanceDto> bowlingPhases = new ArrayList<>();

        if ("TEST".equalsIgnoreCase(format)) {
            phaseNotice = "Match-phase segmentation (Powerplay / Middle / Death overs) is only applicable to limited-overs cricket (ODI and T20I). Test matches do not feature field-restriction powerplays or overs-based phases.";
        } else {
            List<PhasePerformanceDto> allPhases = buildPhasePerformance(key, format);
            battingPhases = allPhases.stream().filter(p -> "BATTING".equals(p.getDiscipline())).toList();
            bowlingPhases = allPhases.stream().filter(p -> "BOWLING".equals(p.getDiscipline())).toList();
        }

        return PlayerStrengthsResponse.builder()
                .playerName(resolvedName)
                .selectedFormat(format)
                .detectedRole(detectedRole)
                .hasBattingMatchups(!batterMatchups.isEmpty())
                .hasBowlingMatchups(!bowlerMatchups.isEmpty())
                .hasPhaseData(!battingPhases.isEmpty() || !bowlingPhases.isEmpty())
                .overallFormatStrikeRate(baselineSr)
                .overallFormatEconomy(baselineEcon)
                .batterMatchups(batterMatchups)
                .bowlerMatchups(bowlerMatchups)
                .battingPhases(battingPhases)
                .bowlingPhases(bowlingPhases)
                .phaseNotice(phaseNotice)
                .totalBowlersFaced(batterMatchups.size())
                .totalBattersFaced(bowlerMatchups.size())
                .build();
    }

    private List<BatterVsBowlerDto> buildBatterMatchups(String playerKey, String format, Double baselineSr) {
        List<BatterMatchupCsvRow> rawRows = batterMatchupsMap.getOrDefault(playerKey, List.of());
        if (rawRows.isEmpty()) return List.of();

        boolean isAll = "ALL".equalsIgnoreCase(format);

        // Filter by format or combine
        Map<String, List<BatterMatchupCsvRow>> byBowler = rawRows.stream()
                .filter(r -> isAll || r.format.equalsIgnoreCase(format))
                .collect(Collectors.groupingBy(r -> r.bowler));

        List<BatterVsBowlerDto> results = new ArrayList<>();
        double refSr = baselineSr != null && baselineSr > 0 ? baselineSr : 100.0;

        for (Map.Entry<String, List<BatterMatchupCsvRow>> entry : byBowler.entrySet()) {
            String bowlerName = entry.getKey();
            List<BatterMatchupCsvRow> list = entry.getValue();

            int matches = list.stream().mapToInt(BatterMatchupCsvRow::matches).sum();
            int innings = list.stream().mapToInt(BatterMatchupCsvRow::innings).sum();
            int ballsFaced = list.stream().mapToInt(BatterMatchupCsvRow::ballsFaced).sum();
            int runs = list.stream().mapToInt(BatterMatchupCsvRow::runs).sum();
            int fours = list.stream().mapToInt(BatterMatchupCsvRow::fours).sum();
            int sixes = list.stream().mapToInt(BatterMatchupCsvRow::sixes).sum();
            int dismissals = list.stream().mapToInt(BatterMatchupCsvRow::dismissals).sum();
            int dotBalls = list.stream().mapToInt(BatterMatchupCsvRow::dotBalls).sum();

            double sr = ballsFaced > 0 ? round2((double) runs / ballsFaced * 100.0) : 0.0;
            double dotPct = ballsFaced > 0 ? round1((double) dotBalls / ballsFaced * 100.0) : 0.0;
            double bndPct = runs > 0 ? round1((double) (fours * 4 + sixes * 6) / runs * 100.0) : 0.0;

            String firstDate = list.stream().map(BatterMatchupCsvRow::firstDate).filter(s -> !s.isBlank()).min(String::compareTo).orElse("");
            String lastDate = list.stream().map(BatterMatchupCsvRow::lastDate).filter(s -> !s.isBlank()).max(String::compareTo).orElse("");

            // Assessment & Sample size
            String badge = ballsFaced >= MIN_BATTER_BALLS ? "SUFFICIENT" : "LIMITED";
            String assessment;

            if (ballsFaced < MIN_BATTER_BALLS) {
                assessment = "LIMITED_DATA";
            } else if (dismissals == 0 && sr >= refSr * 1.15) {
                assessment = "DOMINANT";
            } else if (dismissals >= 2 && sr < refSr * 0.85) {
                assessment = "VULNERABLE";
            } else if (sr >= refSr * 1.10 && dismissals <= 1) {
                assessment = "FAVOURABLE";
            } else if (sr < refSr * 0.90 || dismissals >= 2) {
                assessment = "CHALLENGING";
            } else {
                assessment = "NEUTRAL";
            }

            results.add(BatterVsBowlerDto.builder()
                    .bowler(bowlerName)
                    .format(format)
                    .matches(matches)
                    .innings(innings)
                    .ballsFaced(ballsFaced)
                    .runs(runs)
                    .fours(fours)
                    .sixes(sixes)
                    .strikeRate(sr)
                    .dismissals(dismissals)
                    .dotBalls(dotBalls)
                    .dotPct(dotPct)
                    .boundaryPct(bndPct)
                    .assessment(assessment)
                    .sampleSizeBadge(badge)
                    .firstDate(firstDate)
                    .lastDate(lastDate)
                    .build());
        }

        // Sort by balls faced descending
        results.sort(Comparator.comparingInt(BatterVsBowlerDto::getBallsFaced).reversed());
        return results;
    }

    private List<BowlerVsBatterDto> buildBowlerMatchups(String playerKey, String format, Double baselineEcon) {
        List<BowlerMatchupCsvRow> rawRows = bowlerMatchupsMap.getOrDefault(playerKey, List.of());
        if (rawRows.isEmpty()) return List.of();

        boolean isAll = "ALL".equalsIgnoreCase(format);

        Map<String, List<BowlerMatchupCsvRow>> byBatter = rawRows.stream()
                .filter(r -> isAll || r.format.equalsIgnoreCase(format))
                .collect(Collectors.groupingBy(r -> r.batter));

        List<BowlerVsBatterDto> results = new ArrayList<>();
        double refEcon = baselineEcon != null && baselineEcon > 0 ? baselineEcon : 6.0;

        for (Map.Entry<String, List<BowlerMatchupCsvRow>> entry : byBatter.entrySet()) {
            String batterName = entry.getKey();
            List<BowlerMatchupCsvRow> list = entry.getValue();

            int matches = list.stream().mapToInt(BowlerMatchupCsvRow::matches).sum();
            int innings = list.stream().mapToInt(BowlerMatchupCsvRow::innings).sum();
            int legalBalls = list.stream().mapToInt(BowlerMatchupCsvRow::legalBalls).sum();
            int runsConceded = list.stream().mapToInt(BowlerMatchupCsvRow::runsConceded).sum();
            int wickets = list.stream().mapToInt(BowlerMatchupCsvRow::wickets).sum();
            int dotBalls = list.stream().mapToInt(BowlerMatchupCsvRow::dotBalls).sum();
            int boundaries = list.stream().mapToInt(BowlerMatchupCsvRow::boundariesConceded).sum();

            double decOvers = legalBalls / 6.0;
            double econ = decOvers > 0 ? round2((double) runsConceded / decOvers) : 0.0;
            Double avg = wickets > 0 ? round2((double) runsConceded / wickets) : null;
            Double sr = wickets > 0 ? round1((double) legalBalls / wickets) : null;
            double dotPct = legalBalls > 0 ? round1((double) dotBalls / legalBalls * 100.0) : 0.0;

            String firstDate = list.stream().map(BowlerMatchupCsvRow::firstDate).filter(s -> !s.isBlank()).min(String::compareTo).orElse("");
            String lastDate = list.stream().map(BowlerMatchupCsvRow::lastDate).filter(s -> !s.isBlank()).max(String::compareTo).orElse("");

            String badge = legalBalls >= MIN_BOWLER_BALLS ? "SUFFICIENT" : "LIMITED";
            String assessment;

            if (legalBalls < MIN_BOWLER_BALLS) {
                assessment = "LIMITED_DATA";
            } else if (wickets >= 2 && econ <= refEcon * 0.90) {
                assessment = "DOMINANT_CONTROL";
            } else if (wickets >= 1 && econ <= refEcon * 0.85) {
                assessment = "HIGH_CONTROL";
            } else if (wickets == 0 && econ >= refEcon * 1.25) {
                assessment = "EXPENSIVE";
            } else if (wickets >= 1) {
                assessment = "EFFECTIVE";
            } else {
                assessment = "NEUTRAL";
            }

            results.add(BowlerVsBatterDto.builder()
                    .batter(batterName)
                    .format(format)
                    .matches(matches)
                    .innings(innings)
                    .legalBalls(legalBalls)
                    .runsConceded(runsConceded)
                    .wickets(wickets)
                    .economy(econ)
                    .bowlingAverage(avg)
                    .bowlingStrikeRate(sr)
                    .dotBalls(dotBalls)
                    .dotPct(dotPct)
                    .boundariesConceded(boundaries)
                    .assessment(assessment)
                    .sampleSizeBadge(badge)
                    .firstDate(firstDate)
                    .lastDate(lastDate)
                    .build());
        }

        results.sort(Comparator.comparingInt(BowlerVsBatterDto::getLegalBalls).reversed());
        return results;
    }

    private List<PhasePerformanceDto> buildPhasePerformance(String playerKey, String format) {
        List<PhaseCsvRow> rawRows = phaseMap.getOrDefault(playerKey, List.of());
        if (rawRows.isEmpty()) return List.of();

        boolean isAll = "ALL".equalsIgnoreCase(format);

        // Group by (discipline, phase)
        Map<String, List<PhaseCsvRow>> grouped = rawRows.stream()
                .filter(r -> isAll || r.format.equalsIgnoreCase(format))
                .collect(Collectors.groupingBy(r -> r.discipline + "_" + r.phase));

        List<PhasePerformanceDto> results = new ArrayList<>();
        List<String> phaseOrder = List.of("POWERPLAY", "MIDDLE", "DEATH");

        for (String discipline : List.of("BATTING", "BOWLING")) {
            for (String phase : phaseOrder) {
                String gKey = discipline + "_" + phase;
                List<PhaseCsvRow> list = grouped.getOrDefault(gKey, List.of());
                if (list.isEmpty()) continue;

                int matches = list.stream().mapToInt(PhaseCsvRow::matches).sum();
                int innings = list.stream().mapToInt(PhaseCsvRow::innings).sum();
                int balls = list.stream().mapToInt(PhaseCsvRow::balls).sum();
                int runs = list.stream().mapToInt(PhaseCsvRow::runs).sum();
                int fours = list.stream().mapToInt(PhaseCsvRow::fours).sum();
                int sixes = list.stream().mapToInt(PhaseCsvRow::sixes).sum();
                int wOrD = list.stream().mapToInt(PhaseCsvRow::wicketsOrDismissals).sum();
                int dotBalls = list.stream().mapToInt(PhaseCsvRow::dotBalls).sum();

                double rateStat;
                Double avgStat;
                double dotPct = balls > 0 ? round1((double) dotBalls / balls * 100.0) : 0.0;
                double bndPct = 0.0;

                if ("BATTING".equals(discipline)) {
                    rateStat = balls > 0 ? round2((double) runs / balls * 100.0) : 0.0;
                    avgStat = wOrD > 0 ? round2((double) runs / wOrD) : null;
                    bndPct = runs > 0 ? round1((double) (fours * 4 + sixes * 6) / runs * 100.0) : 0.0;
                } else {
                    double decOvers = balls / 6.0;
                    rateStat = decOvers > 0 ? round2((double) runs / decOvers) : 0.0;
                    avgStat = wOrD > 0 ? round2((double) runs / wOrD) : null;
                }

                String badge = balls >= MIN_PHASE_BALLS ? "SUFFICIENT" : "LIMITED";

                results.add(PhasePerformanceDto.builder()
                        .discipline(discipline)
                        .format(format)
                        .phase(phase)
                        .matches(matches)
                        .innings(innings)
                        .balls(balls)
                        .runs(runs)
                        .fours(fours)
                        .sixes(sixes)
                        .wicketsOrDismissals(wOrD)
                        .dotBalls(dotBalls)
                        .rateStat(rateStat)
                        .avgStat(avgStat)
                        .dotPct(dotPct)
                        .boundaryPct(bndPct)
                        .sampleSizeBadge(badge)
                        .build());
            }
        }

        return results;
    }

    private static int safeInt(String s) {
        try { return (s == null || s.isBlank()) ? 0 : Integer.parseInt(s.trim()); } catch (Exception ignored) { return 0; }
    }

    private static double safeDouble(String s) {
        try { return (s == null || s.isBlank()) ? 0.0 : Double.parseDouble(s.trim()); } catch (Exception ignored) { return 0.0; }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
