package com.cricketprediction.backend.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CricketPlayerInfoData {
    private String id;
    private String name;
    private String dateOfBirth;
    private String role;
    private String battingStyle;
    private String bowlingStyle;
    private String placeOfBirth;
    private String country;
    private String playerImg;
    private List<CricketPlayerStat> stats;
}
