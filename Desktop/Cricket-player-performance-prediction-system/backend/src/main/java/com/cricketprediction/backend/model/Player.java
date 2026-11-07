package com.cricketprediction.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Represents a cricket player known to the system.
 * Player names are stored as-is (human-readable) from the ML dataset.
 */
@Entity
@Table(name = "players")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** External API unique identifier (e.g. "c61d247d-7f77-452c-b495-2813a9cd0ac4"). Unique and nullable. */
    @Column(name = "external_player_id", unique = true)
    private String externalPlayerId;

    /** Full player name as used in the ML dataset (e.g. "V Kohli"). */
    @Column(nullable = false)
    private String name;

    /** Official full name if known (e.g. "Virat Kohli"). */
    private String fullName;

    /** Date of birth. */
    private LocalDate dateOfBirth;

    /** Player nationality / country (e.g. "India"). */
    private String nationality;

    /** Place of birth (e.g. "Delhi"). */
    private String placeOfBirth;

    /** URL to player profile avatar/image. */
    private String profileImageUrl;

    /** Playing role: Batter, Bowler, All-rounder, Wicketkeeper. */
    private String role;

    /** Batting style: Right-hand bat, Left-hand bat. */
    private String battingStyle;

    /** Bowling style: Right-arm fast, Right-arm off break, Left-arm orthodox, etc. */
    private String bowlingStyle;

    /** Current team or franchise (e.g. "Royal Challengers Bengaluru"). */
    private String currentTeam;
}
