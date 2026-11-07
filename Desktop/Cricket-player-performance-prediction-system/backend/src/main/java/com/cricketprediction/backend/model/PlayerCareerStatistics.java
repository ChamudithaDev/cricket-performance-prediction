package com.cricketprediction.backend.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Career aggregated statistics per cricket format (TEST, ODI, T20I, IPL).
 *
 * All numerical and string statistics are nullable because not all players
 * bat and bowl in every format.
 */
@Entity
@Table(name = "player_career_statistics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_format", columnNames = {"player_id", "format"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerCareerStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    /** Normalized format: TEST, ODI, T20I, IPL. */
    @Column(nullable = false, length = 20)
    private String format;

    // --- Batting Statistics ---

    private Integer matches;
    private Integer innings;
    private Integer notOuts;
    private Integer runs;
    private String highestScore;
    private Double average;
    private Integer ballsFaced;
    private Double strikeRate;
    private Integer fours;
    private Integer sixes;
    private Integer fifties;
    private Integer hundreds;
    private Integer twoHundreds;

    // --- Bowling Statistics ---

    private Integer wickets;
    private Integer ballsBowled;
    private Integer runsConceded;
    private Double bowlingAverage;
    private Double economy;
    private Double bowlingStrikeRate;
    private Integer fiveWickets;
    private Integer tenWickets;
    private String bestBowling;
}
