package com.cricketprediction.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lightweight system activity log.
 *
 * Intentionally simple — records who did what and whether it succeeded.
 * Not a replacement for a proper audit framework, but sufficient for the
 * current project stage.
 */
@Entity
@Table(name = "system_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A short description of the action, e.g. "USER_REGISTER", "PREDICTION_REQUEST". */
    @Column(nullable = false)
    private String action;

    /** Email or identifier of the actor. May be "anonymous" for unauthenticated actions. */
    private String username;

    /** When the action occurred. */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** "SUCCESS" or "FAILURE". */
    private String status;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
