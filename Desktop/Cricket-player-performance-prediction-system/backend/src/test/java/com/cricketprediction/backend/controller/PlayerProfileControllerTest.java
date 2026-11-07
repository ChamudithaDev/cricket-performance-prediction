package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.CareerStatisticsDto;
import com.cricketprediction.backend.dto.PlayerProfileResponse;
import com.cricketprediction.backend.dto.external.CricketPlayerSearchItem;
import com.cricketprediction.backend.exception.ExternalApiException;
import com.cricketprediction.backend.exception.GlobalExceptionHandler;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import com.cricketprediction.backend.service.PlayerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlayerProfileControllerTest {

    @Mock
    private PlayerProfileService playerProfileService;

    @InjectMocks
    private PlayerProfileController playerProfileController;

    private MockMvc mockMvc;

    private PlayerProfileResponse sampleProfile;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(playerProfileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleProfile = PlayerProfileResponse.builder()
                .id(1L)
                .externalPlayerId("c61d247d-7f77-452c-b495-2813a9cd0ac4")
                .name("Virat Kohli")
                .fullName("Virat Kohli")
                .dateOfBirth(LocalDate.of(1988, 11, 5))
                .nationality("India")
                .placeOfBirth("Delhi")
                .profileImageUrl("https://example.com/kohli.jpg")
                .role("Batsman")
                .battingStyle("Right Handed Bat")
                .bowlingStyle("Right-arm medium")
                .careerStatistics(Map.of(
                        "ODI", CareerStatisticsDto.builder()
                                .format("ODI")
                                .runs(14181)
                                .matches(295)
                                .average(58.67)
                                .build()
                ))
                .build();
    }

    @Test
    void searchExternalPlayers_returns200() throws Exception {
        when(playerProfileService.searchExternalPlayers("Kohli"))
                .thenReturn(List.of(new CricketPlayerSearchItem("ext-1", "Virat Kohli", "India")));

        mockMvc.perform(get("/api/player-profiles/search")
                        .param("name", "Kohli")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Virat Kohli")));
    }

    @Test
    void syncPlayer_returns200WithFullProfile() throws Exception {
        when(playerProfileService.syncPlayer("c61d247d-7f77-452c-b495-2813a9cd0ac4"))
                .thenReturn(sampleProfile);

        mockMvc.perform(post("/api/player-profiles/sync/c61d247d-7f77-452c-b495-2813a9cd0ac4")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.externalPlayerId", is("c61d247d-7f77-452c-b495-2813a9cd0ac4")))
                .andExpect(jsonPath("$.name", is("Virat Kohli")))
                .andExpect(jsonPath("$.placeOfBirth", is("Delhi")))
                .andExpect(jsonPath("$.careerStatistics.ODI.runs", is(14181)));
    }

    @Test
    void getPlayerProfile_existingId_returns200() throws Exception {
        when(playerProfileService.getPlayerProfile(1L))
                .thenReturn(sampleProfile);

        mockMvc.perform(get("/api/player-profiles/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Virat Kohli")))
                .andExpect(jsonPath("$.careerStatistics.ODI.average", is(58.67)));
    }

    @Test
    void getPlayerProfile_nonExistingId_returns404() throws Exception {
        when(playerProfileService.getPlayerProfile(999L))
                .thenThrow(new ResourceNotFoundException("Player not found with id: 999"));

        mockMvc.perform(get("/api/player-profiles/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("999")));
    }

    @Test
    void syncPlayer_externalApiError_returns502() throws Exception {
        when(playerProfileService.syncPlayer("bad-id"))
                .thenThrow(new ExternalApiException("Cricket Data API timeout or failure."));

        mockMvc.perform(post("/api/player-profiles/sync/bad-id")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", containsString("Cricket Data API timeout or failure")));
    }
}
