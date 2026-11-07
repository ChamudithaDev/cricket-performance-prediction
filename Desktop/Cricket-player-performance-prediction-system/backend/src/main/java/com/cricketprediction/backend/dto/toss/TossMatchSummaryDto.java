package com.cricketprediction.backend.dto.toss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of an individual match evaluated by the Toss Decision Advisor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossMatchSummaryDto {
    private String matchId;
    private String date;
    private String team1;
    private String team2;
    private String tossWinner;
    private String tossDecision;
    private String firstInningsTeam;
    private String secondInningsTeam;
    private String winner;
    private String winByType;
    private double winByMargin;
    private boolean isDecisive;
    private String resultType;
    private String dataQualityFlag;
    private int batFirstWon;
    private int chaseWon;
}
