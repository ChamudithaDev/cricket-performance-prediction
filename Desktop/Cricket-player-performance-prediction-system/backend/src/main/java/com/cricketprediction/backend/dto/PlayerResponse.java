package com.cricketprediction.backend.dto;

import com.cricketprediction.backend.model.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Outbound DTO for player profile information.
 *
 * Deliberately does not expose the JPA entity directly so
 * future entity changes do not silently change the API contract.
 *
 * Null fields are serialised as JSON null (not omitted) to keep
 * the response schema predictable for the React frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerResponse {

    private Long id;
    private String name;
    private String fullName;
    private LocalDate dateOfBirth;
    private String nationality;
    private String profileImageUrl;
    private String role;
    private String battingStyle;
    private String bowlingStyle;
    private String currentTeam;

    /** Map a Player entity to this DTO. */
    public static PlayerResponse from(Player player) {
        if (player == null) {
            return null;
        }
        return PlayerResponse.builder()
                .id(player.getId())
                .name(player.getName())
                .fullName(player.getFullName())
                .dateOfBirth(player.getDateOfBirth())
                .nationality(player.getNationality())
                .profileImageUrl(player.getProfileImageUrl())
                .role(player.getRole())
                .battingStyle(player.getBattingStyle())
                .bowlingStyle(player.getBowlingStyle())
                .currentTeam(player.getCurrentTeam())
                .build();
    }
}
