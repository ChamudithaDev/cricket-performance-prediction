package com.cricketprediction.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores a single evaluation metric row for a deployed ML model.
 *
 * Rather than storing all metrics in one row (which creates schema coupling),
 * each metric is a separate row: (modelName, modelType, version, metricName, metricValue).
 *
 * Example rows:
 *   SVR | regression | 1.0 | test_mae  | 13.7029
 *   SVR | regression | 1.0 | test_rmse | 23.3826
 *   MLP | classification | 1.0 | test_f1 | 0.3816
 */
@Entity
@Table(name = "model_information")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short model identifier — e.g. "SVR", "MLP". */
    @Column(nullable = false)
    private String modelName;

    /** Task type: "regression" or "classification". */
    @Column(nullable = false)
    private String modelType;

    /** Semantic version of this model deployment, e.g. "1.0". */
    private String version;

    /** Name of the evaluation metric, e.g. "test_mae", "test_f1". */
    @Column(nullable = false)
    private String metricName;

    /** Numeric value of the metric. */
    @Column(nullable = false)
    private Double metricValue;

    /** When this record was created. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
