package com.cricketprediction.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Represents a cricket match event.
 * externalMatchId can hold an identifier from a cricket data source
 * so duplicate imports can be detected.
 */
@Entity
@Table(
    name = "matches",
    uniqueConstraints = @UniqueConstraint(columnNames = "external_match_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier from external cricket data source. Nullable for manually created records. */
    @Column(name = "external_match_id")
    private String externalMatchId;

    /** Canonical match type: t20i | odi | test */
    @Column(nullable = false)
    private String matchType;

    private LocalDate date;

    private String venue;

    /** The team whose performance is being recorded. */
    private String team;

    /** The opposing team. */
    private String opposition;
}
