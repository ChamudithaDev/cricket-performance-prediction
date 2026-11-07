package com.cricketprediction.backend.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PredictionHistoryItemDto
 *
 * Safe user-facing item for past predictions history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionHistoryItemDto {
    private Long id;
    private String player;
    private String opponent;
    private String venue;
    private String matchType;
    private Double predictedRuns;
    private String predictedTier;
    private String modelName;
    private String classifierName;
    private LocalDateTime predictionDate;
}
