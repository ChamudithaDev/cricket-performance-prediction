package com.cricketprediction.backend.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * PredictionResponseDto
 *
 * Encapsulates the results of both the Regression task (predicted runs)
 * and the Classification task (predicted performance tier and probabilities).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResponseDto {

    private Long id;

    private String player;

    private String opponent;

    private String venue;

    @JsonProperty("match_type")
    private String match_type;

    @JsonProperty("predicted_runs")
    private Double predicted_runs;

    @JsonProperty("regression_derived_tier")
    private String regression_derived_tier;

    @JsonProperty("predicted_tier")
    private String predicted_tier;

    @JsonProperty("tier_probabilities")
    private Map<String, Double> tier_probabilities;

    @JsonProperty("model_name")
    private String model_name;

    @JsonProperty("classifier_name")
    private String classifier_name;

    @JsonProperty("prediction_date")
    private String prediction_date;

    /** Backward-compatible helper for integer casts if called by legacy callers */
    public int getPredictedRunsAsInt() {
        return predicted_runs != null ? (int) Math.round(predicted_runs) : 0;
    }
}
