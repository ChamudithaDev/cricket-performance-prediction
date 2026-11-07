package com.cricketprediction.backend.dto.toss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * List of available venues for a format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossVenuesResponse {
    private String format;
    private List<String> venues;
    private int totalVenues;
}
