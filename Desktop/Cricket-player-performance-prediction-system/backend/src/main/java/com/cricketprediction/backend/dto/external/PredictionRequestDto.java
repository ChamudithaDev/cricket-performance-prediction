package com.cricketprediction.backend.dto.external;

import jakarta.validation.constraints.NotBlank;

public class PredictionRequestDto {
    @NotBlank(message = "player must not be blank")
    private String player;
    private String venue; // optional
    @NotBlank(message = "opponent must not be blank")
    private String opponent;
    @NotBlank(message = "match_type must not be blank")
    private String match_type;

    public PredictionRequestDto() {}

    public PredictionRequestDto(String player, String venue, String opponent, String match_type) {
        this.player = player;
        this.venue = venue;
        this.opponent = opponent;
        this.match_type = match_type;
    }

    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getOpponent() { return opponent; }
    public void setOpponent(String opponent) { this.opponent = opponent; }
    public String getMatch_type() { return match_type; }
    public void setMatch_type(String match_type) { this.match_type = match_type; }
}
