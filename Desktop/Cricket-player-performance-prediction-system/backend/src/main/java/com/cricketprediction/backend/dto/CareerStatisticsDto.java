package com.cricketprediction.backend.dto;

import com.cricketprediction.backend.model.PlayerCareerStatistics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerStatisticsDto {

    private String format;

    // Batting
    private Integer matches;
    private Integer innings;
    private Integer notOuts;
    private Integer runs;
    private String highestScore;
    private Double average;
    private Integer ballsFaced;
    private Double strikeRate;
    private Integer fours;
    private Integer sixes;
    private Integer fifties;
    private Integer hundreds;
    private Integer twoHundreds;

    // Bowling
    private Integer wickets;
    private Integer ballsBowled;
    private Integer runsConceded;
    private Double bowlingAverage;
    private Double economy;
    private Double bowlingStrikeRate;
    private Integer fiveWickets;
    private Integer tenWickets;
    private String bestBowling;

    public static CareerStatisticsDto from(PlayerCareerStatistics stats) {
        if (stats == null) {
            return null;
        }
        return CareerStatisticsDto.builder()
                .format(stats.getFormat())
                .matches(stats.getMatches())
                .innings(stats.getInnings())
                .notOuts(stats.getNotOuts())
                .runs(stats.getRuns())
                .highestScore(stats.getHighestScore())
                .average(stats.getAverage())
                .ballsFaced(stats.getBallsFaced())
                .strikeRate(stats.getStrikeRate())
                .fours(stats.getFours())
                .sixes(stats.getSixes())
                .fifties(stats.getFifties())
                .hundreds(stats.getHundreds())
                .twoHundreds(stats.getTwoHundreds())
                .wickets(stats.getWickets())
                .ballsBowled(stats.getBallsBowled())
                .runsConceded(stats.getRunsConceded())
                .bowlingAverage(stats.getBowlingAverage())
                .economy(stats.getEconomy())
                .bowlingStrikeRate(stats.getBowlingStrikeRate())
                .fiveWickets(stats.getFiveWickets())
                .tenWickets(stats.getTenWickets())
                .bestBowling(stats.getBestBowling())
                .build();
    }
}
