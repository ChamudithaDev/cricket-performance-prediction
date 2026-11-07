package com.cricketprediction.backend.controller;

import com.cricketprediction.backend.dto.PlayerResponse;
import com.cricketprediction.backend.exception.GlobalExceptionHandler;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import com.cricketprediction.backend.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for PlayerController using MockMvc standaloneSetup.
 *
 * Standalone setup means: no Spring context, no security filter chain.
 * JWT protection is tested separately via SecurityConfig.
 */
@ExtendWith(MockitoExtension.class)
class PlayerControllerTest {

    @Mock
    PlayerService playerService;

    @InjectMocks
    PlayerController playerController;

    MockMvc mockMvc;

    private PlayerResponse kohliDto;
    private PlayerResponse dhoniDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders
                .standaloneSetup(playerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();

        kohliDto = PlayerResponse.builder()
                .id(1L)
                .name("V Kohli")
                .fullName("Virat Kohli")
                .dateOfBirth(LocalDate.of(1988, 11, 5))
                .nationality("India")
                .profileImageUrl("https://example.com/kohli.jpg")
                .role("Batter")
                .battingStyle("Right-hand bat")
                .bowlingStyle("Right-arm medium")
                .currentTeam("India")
                .build();

        dhoniDto = PlayerResponse.builder()
                .id(2L)
                .name("MS Dhoni")
                .currentTeam("India")
                .role("Wicketkeeper")
                .battingStyle("Right-hand bat")
                .build();
    }

    // ------------------------------------------------------------------
    // Test 1 — GET /api/players → 200 with player list
    // ------------------------------------------------------------------

    @Test
    void getAllPlayers_returns200WithContent() throws Exception {
        when(playerService.getAllPlayers()).thenReturn(List.of(kohliDto, dhoniDto));

        mockMvc.perform(get("/api/players").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("V Kohli")))
                .andExpect(jsonPath("$[1].name", is("MS Dhoni")));
    }

    @Test
    void getAllPlayers_emptyDatabase_returns200WithEmptyList() throws Exception {
        when(playerService.getAllPlayers()).thenReturn(List.of());

        mockMvc.perform(get("/api/players").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------
    // Test 2 — GET /api/players/{id} found → 200
    // ------------------------------------------------------------------

    @Test
    void getPlayerById_existingId_returns200() throws Exception {
        when(playerService.getPlayerById(1L)).thenReturn(kohliDto);

        mockMvc.perform(get("/api/players/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("V Kohli")))
                .andExpect(jsonPath("$.fullName", is("Virat Kohli")))
                .andExpect(jsonPath("$.dateOfBirth", is("1988-11-05")))
                .andExpect(jsonPath("$.nationality", is("India")))
                .andExpect(jsonPath("$.profileImageUrl", is("https://example.com/kohli.jpg")))
                .andExpect(jsonPath("$.currentTeam", is("India")))
                .andExpect(jsonPath("$.role", is("Batter")))
                .andExpect(jsonPath("$.battingStyle", is("Right-hand bat")))
                .andExpect(jsonPath("$.bowlingStyle", is("Right-arm medium")));
    }

    // ------------------------------------------------------------------
    // Test 3 — GET /api/players/{id} not found → 404
    // ------------------------------------------------------------------

    @Test
    void getPlayerById_nonExistingId_returns404WithErrorBody() throws Exception {
        when(playerService.getPlayerById(999L))
                .thenThrow(new ResourceNotFoundException("Player not found with id: 999"));

        mockMvc.perform(get("/api/players/999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("999")));
    }

    // ------------------------------------------------------------------
    // Test 4 — GET /api/players/search?name=Kohli → 200 with match
    // ------------------------------------------------------------------

    @Test
    void searchPlayers_partialMatch_returns200WithResults() throws Exception {
        when(playerService.searchByName("Kohli")).thenReturn(List.of(kohliDto));

        mockMvc.perform(get("/api/players/search").param("name", "Kohli")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("V Kohli")));
    }

    // ------------------------------------------------------------------
    // Test 5 — Case-insensitive search
    // ------------------------------------------------------------------

    @Test
    void searchPlayers_lowercaseQuery_returns200() throws Exception {
        when(playerService.searchByName("kohli")).thenReturn(List.of(kohliDto));

        mockMvc.perform(get("/api/players/search").param("name", "kohli")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("V Kohli")));
    }

    @Test
    void searchPlayers_uppercaseQuery_returns200() throws Exception {
        when(playerService.searchByName("KOHLI")).thenReturn(List.of(kohliDto));

        mockMvc.perform(get("/api/players/search").param("name", "KOHLI")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("V Kohli")));
    }

    @Test
    void searchPlayers_blankName_returns200EmptyList() throws Exception {
        when(playerService.searchByName(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/players/search").param("name", "")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------
    // Test 7 — Null bowlingStyle is serialised as JSON null (not omitted)
    // ------------------------------------------------------------------

    @Test
    void getPlayerById_nullFieldSerializedAsNull() throws Exception {
        when(playerService.getPlayerById(2L)).thenReturn(dhoniDto);

        mockMvc.perform(get("/api/players/2").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bowlingStyle").doesNotExist());
    }
}
