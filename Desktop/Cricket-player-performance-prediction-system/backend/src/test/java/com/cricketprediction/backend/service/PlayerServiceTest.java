package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.PlayerResponse;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import com.cricketprediction.backend.model.Player;
import com.cricketprediction.backend.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlayerService.
 * No Spring context — all dependencies are mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    PlayerRepository playerRepository;

    @InjectMocks
    PlayerService playerService;

    private Player kohli;
    private Player dhoni;

    @BeforeEach
    void setUp() {
        kohli = Player.builder()
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

        dhoni = Player.builder()
                .id(2L)
                .name("MS Dhoni")
                .currentTeam("India")
                .role("Wicketkeeper")
                .battingStyle("Right-hand bat")
                .build();
    }

    // ------------------------------------------------------------------
    // getAllPlayers tests
    // ------------------------------------------------------------------

    @Test
    void getAllPlayers_returnsSortedListOfDtos() {
        when(playerRepository.findAll(Sort.by("name").ascending()))
                .thenReturn(List.of(dhoni, kohli));  // repo returns in any order

        List<PlayerResponse> result = playerService.getAllPlayers();

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting("name")
                .containsExactly("MS Dhoni", "V Kohli");
    }

    @Test
    void getAllPlayers_emptyDatabase_returnsEmptyList() {
        when(playerRepository.findAll(Sort.by("name").ascending()))
                .thenReturn(List.of());

        List<PlayerResponse> result = playerService.getAllPlayers();

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // getPlayerById tests
    // ------------------------------------------------------------------

    @Test
    void getPlayerById_existingId_returnsDto() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(kohli));

        PlayerResponse response = playerService.getPlayerById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("V Kohli");
        assertThat(response.getFullName()).isEqualTo("Virat Kohli");
        assertThat(response.getDateOfBirth()).isEqualTo(LocalDate.of(1988, 11, 5));
        assertThat(response.getNationality()).isEqualTo("India");
        assertThat(response.getProfileImageUrl()).isEqualTo("https://example.com/kohli.jpg");
        assertThat(response.getCurrentTeam()).isEqualTo("India");
        assertThat(response.getRole()).isEqualTo("Batter");
        assertThat(response.getBattingStyle()).isEqualTo("Right-hand bat");
        assertThat(response.getBowlingStyle()).isEqualTo("Right-arm medium");
    }

    @Test
    void getPlayerById_nonExistingId_throwsResourceNotFoundException() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayerById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ------------------------------------------------------------------
    // searchByName tests (Test 4 and 5 from requirements)
    // ------------------------------------------------------------------

    @Test
    void searchByName_partialMatch_returnsMatchingPlayers() {
        when(playerRepository.findByNameContainingIgnoreCase("Kohli"))
                .thenReturn(List.of(kohli));

        List<PlayerResponse> result = playerService.searchByName("Kohli");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("V Kohli");
    }

    @Test
    void searchByName_caseInsensitive_lowercaseQuery() {
        when(playerRepository.findByNameContainingIgnoreCase("kohli"))
                .thenReturn(List.of(kohli));

        List<PlayerResponse> result = playerService.searchByName("kohli");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("V Kohli");
    }

    @Test
    void searchByName_caseInsensitive_uppercaseQuery() {
        when(playerRepository.findByNameContainingIgnoreCase("KOHLI"))
                .thenReturn(List.of(kohli));

        List<PlayerResponse> result = playerService.searchByName("KOHLI");
        assertThat(result).hasSize(1);
    }

    @Test
    void searchByName_blankQuery_returnsEmptyList() {
        List<PlayerResponse> result = playerService.searchByName("   ");
        assertThat(result).isEmpty();
        verifyNoInteractions(playerRepository);
    }

    @Test
    void searchByName_nullQuery_returnsEmptyList() {
        List<PlayerResponse> result = playerService.searchByName(null);
        assertThat(result).isEmpty();
        verifyNoInteractions(playerRepository);
    }

    @Test
    void searchByName_noMatch_returnsEmptyList() {
        when(playerRepository.findByNameContainingIgnoreCase("Bradman"))
                .thenReturn(List.of());

        List<PlayerResponse> result = playerService.searchByName("Bradman");
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // DTO mapping test
    // ------------------------------------------------------------------

    @Test
    void playerResponse_mapsAllFields() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(kohli));

        PlayerResponse dto = playerService.getPlayerById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("V Kohli");
        assertThat(dto.getFullName()).isEqualTo("Virat Kohli");
        assertThat(dto.getDateOfBirth()).isEqualTo(LocalDate.of(1988, 11, 5));
        assertThat(dto.getNationality()).isEqualTo("India");
        assertThat(dto.getProfileImageUrl()).isEqualTo("https://example.com/kohli.jpg");
        assertThat(dto.getRole()).isEqualTo("Batter");
        assertThat(dto.getBattingStyle()).isEqualTo("Right-hand bat");
        assertThat(dto.getBowlingStyle()).isEqualTo("Right-arm medium");
        assertThat(dto.getCurrentTeam()).isEqualTo("India");
    }

    @Test
    void playerResponse_nullableFields_mappedAsNull() {
        when(playerRepository.findById(2L)).thenReturn(Optional.of(dhoni));

        PlayerResponse dto = playerService.getPlayerById(2L);

        assertThat(dto.getId()).isEqualTo(2L);
        assertThat(dto.getName()).isEqualTo("MS Dhoni");
        assertThat(dto.getFullName()).isNull();
        assertThat(dto.getDateOfBirth()).isNull();
        assertThat(dto.getNationality()).isNull();
        assertThat(dto.getProfileImageUrl()).isNull();
        assertThat(dto.getBowlingStyle()).isNull();
        assertThat(dto.getCurrentTeam()).isEqualTo("India");
    }
}
