package com.cricketprediction.backend.dto.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PredictionMetadataDto
 *
 * Provides validated options for players, venues, opponents, and match formats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionMetadataDto {
    private List<String> players;
    private List<String> venues;
    private List<String> opponents;
    private List<String> matchTypes;
}
