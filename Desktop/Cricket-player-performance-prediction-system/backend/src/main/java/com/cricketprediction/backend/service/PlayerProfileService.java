package com.cricketprediction.backend.service;

import com.cricketprediction.backend.client.CricketDataApiClient;
import com.cricketprediction.backend.dto.PlayerProfileResponse;
import com.cricketprediction.backend.dto.external.CricketPlayerInfoData;
import com.cricketprediction.backend.dto.external.CricketPlayerSearchItem;
import com.cricketprediction.backend.dto.external.CricketPlayerStat;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import com.cricketprediction.backend.model.Player;
import com.cricketprediction.backend.model.PlayerCareerStatistics;
import com.cricketprediction.backend.repository.PlayerCareerStatisticsRepository;
import com.cricketprediction.backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Service managing player profile integration with Cricket Data API.
 *
 * Handles:
 *   - Search external players without automatic database persistence
 *   - Sync/import external player details and format-specific career statistics
 *   - Retrieve complete locally stored player profile with career statistics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerProfileService {

    private final CricketDataApiClient cricketDataApiClient;
    private final PlayerRepository playerRepository;
    private final PlayerCareerStatisticsRepository careerStatisticsRepository;

    /**
     * Search external Cricket Data API without persisting to the database.
     *
     * @param name search query
     * @return search items from external API
     */
    public List<CricketPlayerSearchItem> searchExternalPlayers(String name) {
        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }
        return cricketDataApiClient.searchPlayers(name.trim());
    }

    /**
     * Sync or import a player from Cricket Data API by external UUID.
     *
     * Updates or creates the Player entity and maps/updates format-based career statistics.
     *
     * @param externalPlayerId external player UUID
     * @return complete PlayerProfileResponse
     */
    @Transactional
    public PlayerProfileResponse syncPlayer(String externalPlayerId) {
        if (externalPlayerId == null || externalPlayerId.isBlank()) {
            throw new IllegalArgumentException("External player ID must not be blank.");
        }

        CricketPlayerInfoData info = cricketDataApiClient.getPlayerInfo(externalPlayerId.trim());

        // 1. Locate existing player by external ID or name fallback
        Player player = playerRepository.findByExternalPlayerId(info.getId())
                .orElseGet(() -> playerRepository.findByName(info.getName())
                        .orElseGet(() -> Player.builder().name(info.getName()).build()));

        // 2. Map and update basic profile fields
        player.setExternalPlayerId(info.getId());
        if (player.getName() == null || player.getName().isBlank()) {
            player.setName(info.getName());
        }
        player.setFullName(info.getName());

        LocalDate dob = parseDateOfBirth(info.getDateOfBirth());
        if (dob != null) {
            player.setDateOfBirth(dob);
        }

        if (info.getCountry() != null && !info.getCountry().isBlank()) {
            player.setNationality(info.getCountry().trim());
        }

        if (info.getPlaceOfBirth() != null && !info.getPlaceOfBirth().isBlank()) {
            player.setPlaceOfBirth(info.getPlaceOfBirth().trim());
        }

        if (info.getPlayerImg() != null && !info.getPlayerImg().isBlank()) {
            player.setProfileImageUrl(info.getPlayerImg().trim());
        }

        if (info.getRole() != null && !info.getRole().isBlank()) {
            player.setRole(info.getRole().trim());
        }

        if (info.getBattingStyle() != null && !info.getBattingStyle().isBlank()) {
            player.setBattingStyle(info.getBattingStyle().trim());
        }

        if (info.getBowlingStyle() != null && !info.getBowlingStyle().isBlank()) {
            player.setBowlingStyle(info.getBowlingStyle().trim());
        }

        // Save Player entity
        player = playerRepository.save(player);
        log.info("Persisted player id={} (name='{}', externalId='{}')",
                player.getId(), player.getName(), player.getExternalPlayerId());

        // 3. Process and map career statistics by format
        List<PlayerCareerStatistics> savedStats = processAndSaveCareerStatistics(player, info.getStats());

        return PlayerProfileResponse.from(player, savedStats);
    }

    /**
     * Retrieve complete local player profile and career statistics.
     *
     * @param playerId local player ID
     * @return complete PlayerProfileResponse
     * @throws ResourceNotFoundException if player not found
     */
    @Transactional(readOnly = true)
    public PlayerProfileResponse getPlayerProfile(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found with id: " + playerId));

        List<PlayerCareerStatistics> stats = careerStatisticsRepository.findByPlayerId(playerId);
        return PlayerProfileResponse.from(player, stats);
    }

    /**
     * Map raw external stats[] into PlayerCareerStatistics by normalized format.
     *
     * Format normalization:
     *   - "test" -> "TEST"
     *   - "odi"  -> "ODI"
     *   - "t20i" -> "T20I"
     *   - "t20"  -> "T20I"
     *   - "ipl"  -> "IPL"
     *
     * Precedence Strategy:
     *   If both "t20" and "t20i" match types are present in the response, items with matchtype "t20"
     *   are processed first, followed by "t20i", ensuring international T20I values take precedence
     *   over domestic T20 numbers while retaining non-null stats.
     */
    private List<PlayerCareerStatistics> processAndSaveCareerStatistics(Player player, List<CricketPlayerStat> stats) {
        if (stats == null || stats.isEmpty()) {
            return careerStatisticsRepository.findByPlayerId(player.getId());
        }

        // Existing stats mapped by format
        Map<String, PlayerCareerStatistics> formatStatsMap = new LinkedHashMap<>();
        for (PlayerCareerStatistics existing : careerStatisticsRepository.findByPlayerId(player.getId())) {
            formatStatsMap.put(existing.getFormat(), existing);
        }

        // Sort items so "t20" comes before "t20i" (t20i takes precedence on overwrite)
        List<CricketPlayerStat> sortedStats = new ArrayList<>(stats);
        sortedStats.sort(Comparator.comparingInt(s -> {
            String m = s.getMatchtype() != null ? s.getMatchtype().trim().toLowerCase() : "";
            return "t20i".equals(m) ? 1 : 0;
        }));

        for (CricketPlayerStat item : sortedStats) {
            String normalizedFormat = normalizeFormat(item.getMatchtype());
            if (normalizedFormat == null) {
                // Ignore unsupported formats safely
                continue;
            }

            PlayerCareerStatistics statRecord = formatStatsMap.computeIfAbsent(normalizedFormat, fmt ->
                    PlayerCareerStatistics.builder()
                            .player(player)
                            .format(fmt)
                            .build()
            );

            applyStat(statRecord, item);
        }

        // Save all format records
        List<PlayerCareerStatistics> saved = careerStatisticsRepository.saveAll(new ArrayList<>(formatStatsMap.values()));
        return saved;
    }

    /**
     * Normalize format name.
     */
    public String normalizeFormat(String matchtype) {
        if (matchtype == null) {
            return null;
        }
        String clean = matchtype.trim().toLowerCase();
        return switch (clean) {
            case "test" -> "TEST";
            case "odi" -> "ODI";
            case "t20i", "t20" -> "T20I";
            case "ipl" -> "IPL";
            default -> null; // Ignore other unrecognised tournament categories safely
        };
    }

    /**
     * Apply individual stat to the entity according to function (batting/bowling) and stat key.
     */
    private void applyStat(PlayerCareerStatistics record, CricketPlayerStat statItem) {
        if (statItem == null || statItem.getStat() == null) {
            return;
        }

        String fn = statItem.getFn() != null ? statItem.getFn().trim().toLowerCase() : "";
        String stat = statItem.getStat().trim().toLowerCase();
        String val = statItem.getValue();

        if ("batting".equals(fn)) {
            switch (stat) {
                case "m" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setMatches(v);
                }
                case "inn" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setInnings(v);
                }
                case "no" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setNotOuts(v);
                }
                case "runs" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setRuns(v);
                }
                case "hs" -> {
                    if (val != null && !val.trim().equals("-")) record.setHighestScore(val.trim());
                }
                case "avg" -> {
                    Double v = parseDouble(val);
                    if (v != null) record.setAverage(v);
                }
                case "bf" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setBallsFaced(v);
                }
                case "sr" -> {
                    Double v = parseDouble(val);
                    if (v != null) record.setStrikeRate(v);
                }
                case "4s" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setFours(v);
                }
                case "6s" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setSixes(v);
                }
                case "50s" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setFifties(v);
                }
                case "100s" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setHundreds(v);
                }
                case "200s" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setTwoHundreds(v);
                }
                default -> {
                    // Ignore unsupported batting stats
                }
            }
        } else if ("bowling".equals(fn)) {
            switch (stat) {
                case "m" -> {
                    Integer v = parseInteger(val);
                    if (v != null && record.getMatches() == null) record.setMatches(v);
                }
                case "inn" -> {
                    Integer v = parseInteger(val);
                    if (v != null && record.getInnings() == null) record.setInnings(v);
                }
                case "b" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setBallsBowled(v);
                }
                case "runs" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setRunsConceded(v);
                }
                case "wkts" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setWickets(v);
                }
                case "avg" -> {
                    Double v = parseDouble(val);
                    if (v != null) record.setBowlingAverage(v);
                }
                case "econ" -> {
                    Double v = parseDouble(val);
                    if (v != null) record.setEconomy(v);
                }
                case "sr" -> {
                    Double v = parseDouble(val);
                    if (v != null) record.setBowlingStrikeRate(v);
                }
                case "5w" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setFiveWickets(v);
                }
                case "10w" -> {
                    Integer v = parseInteger(val);
                    if (v != null) record.setTenWickets(v);
                }
                case "bbi" -> {
                    if (val != null && !val.trim().equals("-")) record.setBestBowling(val.trim());
                }
                default -> {
                    // Ignore unsupported bowling stats
                }
            }
        }
    }

    private Integer parseInteger(String val) {
        if (val == null) return null;
        String trimmed = val.trim().replace(",", "");
        if (trimmed.isEmpty() || "-".equals(trimmed) || "null".equalsIgnoreCase(trimmed) || "n/a".equalsIgnoreCase(trimmed)) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String val) {
        if (val == null) return null;
        String trimmed = val.trim().replace(",", "");
        if (trimmed.isEmpty() || "-".equals(trimmed) || "null".equalsIgnoreCase(trimmed) || "n/a".equalsIgnoreCase(trimmed)) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDateOfBirth(String dobStr) {
        if (dobStr == null || dobStr.isBlank()) {
            return null;
        }
        try {
            String trimmed = dobStr.trim();
            if (trimmed.length() >= 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
                return LocalDate.parse(trimmed.substring(0, 10));
            }
            return LocalDate.parse(trimmed);
        } catch (Exception e) {
            log.warn("Could not parse dateOfBirth: {}", dobStr);
            return null;
        }
    }
}
