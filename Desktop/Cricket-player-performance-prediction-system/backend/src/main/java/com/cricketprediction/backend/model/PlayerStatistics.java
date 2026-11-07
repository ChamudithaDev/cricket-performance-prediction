package com.cricketprediction.backend.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Historical per-player per-match performance statistics.
 *
 * All batting/bowling fields are nullable because:
 *   - A pure bowler may not bat (null batting stats)
 *   - A pure batsman may not bowl (null bowling stats)
 */
@Entity
@Table(name = "player_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The player this record belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    /** The match this record belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    // --- Batting ---

    private Integer runs;
    private Integer ballsFaced;
    private Integer fours;
    private Integer sixes;
    private Double strikeRate;

    // --- Bowling ---

    private Integer wickets;
    private Double overs;
    private Integer runsConceded;
    private Double economy;
}
