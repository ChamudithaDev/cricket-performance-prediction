package com.cricketprediction.backend.dto;

import com.cricketprediction.backend.model.Player;
import com.cricketprediction.backend.model.PlayerCareerStatistics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbound DTO for full player profile with career statistics grouped by format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerProfileResponse {

    private Long id;
    private String externalPlayerId;
    private String name;
    private String fullName;
    private LocalDate dateOfBirth;
    private String nationality;
    private String placeOfBirth;
    private String profileImageUrl;
    private String role;
    private String battingStyle;
    private String bowlingStyle;
    private String currentTeam;

    /** Career statistics grouped by format, e.g. "TEST", "ODI", "T20I", "IPL". */
    private Map<String, CareerStatisticsDto> careerStatistics;

    public static PlayerProfileResponse from(Player player, List<PlayerCareerStatistics> statisticsList) {
        if (player == null) {
            return null;
        }

        Map<String, CareerStatisticsDto> statsMap = new LinkedHashMap<>();
        if (statisticsList != null) {
            for (PlayerCareerStatistics stats : statisticsList) {
                if (stats.getFormat() != null) {
                    statsMap.put(stats.getFormat(), CareerStatisticsDto.from(stats));
                }
            }
        }

        return PlayerProfileResponse.builder()
                .id(player.getId())
                .externalPlayerId(player.getExternalPlayerId())
                .name(player.getName())
                .fullName(player.getFullName())
                .dateOfBirth(player.getDateOfBirth())
                .nationality(player.getNationality())
                .placeOfBirth(player.getPlaceOfBirth())
                .profileImageUrl(player.getProfileImageUrl())
                .role(player.getRole())
                .battingStyle(player.getBattingStyle())
                .bowlingStyle(player.getBowlingStyle())
                .currentTeam(player.getCurrentTeam())
                .careerStatistics(statsMap)
                .build();
    }
}
