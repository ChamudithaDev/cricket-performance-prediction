package com.cricketprediction.backend.service;

import com.cricketprediction.backend.client.CricketDataApiClient;
import com.cricketprediction.backend.dto.CareerStatisticsDto;
import com.cricketprediction.backend.dto.PlayerProfileResponse;
import com.cricketprediction.backend.dto.external.CricketPlayerInfoData;
import com.cricketprediction.backend.dto.external.CricketPlayerSearchItem;
import com.cricketprediction.backend.dto.external.CricketPlayerStat;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import com.cricketprediction.backend.model.Player;
import com.cricketprediction.backend.model.PlayerCareerStatistics;
import com.cricketprediction.backend.repository.PlayerCareerStatisticsRepository;
import com.cricketprediction.backend.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerProfileServiceTest {

    @Mock
    private CricketDataApiClient cricketDataApiClient;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerCareerStatisticsRepository careerStatisticsRepository;

    @InjectMocks
    private PlayerProfileService profileService;

    private CricketPlayerInfoData sampleInfo;

    @BeforeEach
    void setUp() {
        sampleInfo = CricketPlayerInfoData.builder()
                .id("c61d247d-7f77-452c-b495-2813a9cd0ac4")
                .name("Virat Kohli")
                .dateOfBirth("1988-11-05T00:00:00")
                .country("India")
                .placeOfBirth("Delhi")
                .playerImg("https://example.com/kohli.jpg")
                .role("Batsman")
                .battingStyle("Right Handed Bat")
                .bowlingStyle("Right-arm medium")
                .stats(List.of(
                        CricketPlayerStat.builder().fn("batting").matchtype("odi").stat("m").value("295").build(),
                        CricketPlayerStat.builder().fn("batting").matchtype("odi").stat(" runs ").value("14181").build(),
                        CricketPlayerStat.builder().fn("batting").matchtype("odi").stat("avg").value("58.67").build(),
                        CricketPlayerStat.builder().fn("batting").matchtype("odi").stat("hs").value("183").build(),
                        CricketPlayerStat.builder().fn("bowling").matchtype("odi").stat("wkts").value("5").build(),
                        CricketPlayerStat.builder().fn("bowling").matchtype("odi").stat("avg").value("166.2").build()
                ))
                .build();
    }

    @Test
    void searchExternalPlayers_delegatesToClient() {
        when(cricketDataApiClient.searchPlayers("Kohli"))
                .thenReturn(List.of(new CricketPlayerSearchItem("ext-1", "Virat Kohli", "India")));

        List<CricketPlayerSearchItem> result = profileService.searchExternalPlayers("Kohli");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Virat Kohli");
        verifyNoInteractions(playerRepository);
    }

    @Test
    void searchExternalPlayers_blankName_returnsEmptyList() {
        List<CricketPlayerSearchItem> result = profileService.searchExternalPlayers("   ");
        assertThat(result).isEmpty();
        verifyNoInteractions(cricketDataApiClient);
    }

    @Test
    void syncPlayer_newPlayer_createsAndSavesPlayerWithStats() {
        when(cricketDataApiClient.getPlayerInfo("c61d247d-7f77-452c-b495-2813a9cd0ac4"))
                .thenReturn(sampleInfo);
        when(playerRepository.findByExternalPlayerId("c61d247d-7f77-452c-b495-2813a9cd0ac4"))
                .thenReturn(Optional.empty());
        when(playerRepository.findByName("Virat Kohli")).thenReturn(Optional.empty());

        Player savedPlayer = Player.builder()
                .id(1L)
                .externalPlayerId("c61d247d-7f77-452c-b495-2813a9cd0ac4")
                .name("Virat Kohli")
                .dateOfBirth(LocalDate.of(1988, 11, 5))
                .nationality("India")
                .placeOfBirth("Delhi")
                .build();
        when(playerRepository.save(any(Player.class))).thenReturn(savedPlayer);

        when(careerStatisticsRepository.findByPlayerId(1L)).thenReturn(Collections.emptyList());
        when(careerStatisticsRepository.saveAll(any())).thenAnswer(invocation -> new java.util.ArrayList<>(invocation.getArgument(0)));

        PlayerProfileResponse response = profileService.syncPlayer("c61d247d-7f77-452c-b495-2813a9cd0ac4");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getExternalPlayerId()).isEqualTo("c61d247d-7f77-452c-b495-2813a9cd0ac4");
        assertThat(response.getName()).isEqualTo("Virat Kohli");
        assertThat(response.getDateOfBirth()).isEqualTo(LocalDate.of(1988, 11, 5));
        assertThat(response.getPlaceOfBirth()).isEqualTo("Delhi");

        // Verify career statistics mapping
        assertThat(response.getCareerStatistics()).containsKey("ODI");
        CareerStatisticsDto odi = response.getCareerStatistics().get("ODI");
        assertThat(odi.getMatches()).isEqualTo(295);
        assertThat(odi.getRuns()).isEqualTo(14181);
        assertThat(odi.getAverage()).isEqualTo(58.67);
        assertThat(odi.getHighestScore()).isEqualTo("183");
        assertThat(odi.getWickets()).isEqualTo(5);
        assertThat(odi.getBowlingAverage()).isEqualTo(166.2);
    }

    @Test
    void syncPlayer_existingPlayer_updatesWithoutDuplication() {
        Player existing = Player.builder()
                .id(10L)
                .externalPlayerId("c61d247d-7f77-452c-b495-2813a9cd0ac4")
                .name("V Kohli")
                .currentTeam("Royal Challengers Bengaluru")
                .build();

        when(cricketDataApiClient.getPlayerInfo("c61d247d-7f77-452c-b495-2813a9cd0ac4"))
                .thenReturn(sampleInfo);
        when(playerRepository.findByExternalPlayerId("c61d247d-7f77-452c-b495-2813a9cd0ac4"))
                .thenReturn(Optional.of(existing));
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerStatisticsRepository.findByPlayerId(10L)).thenReturn(Collections.emptyList());
        when(careerStatisticsRepository.saveAll(any())).thenAnswer(invocation -> new java.util.ArrayList<>(invocation.getArgument(0)));

        PlayerProfileResponse response = profileService.syncPlayer("c61d247d-7f77-452c-b495-2813a9cd0ac4");

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCurrentTeam()).isEqualTo("Royal Challengers Bengaluru"); // Preserved
        verify(playerRepository, times(1)).save(existing);
    }

    @Test
    void syncPlayer_duplicateStatsT20andT20I_t20iTakesPrecedence() {
        sampleInfo.setStats(List.of(
                CricketPlayerStat.builder().fn("batting").matchtype("t20").stat("runs").value("12000").build(),
                CricketPlayerStat.builder().fn("batting").matchtype("t20i").stat("runs").value("4188").build(),
                CricketPlayerStat.builder().fn("batting").matchtype("t20").stat("hs").value("122").build()
        ));

        Player player = Player.builder().id(1L).externalPlayerId(sampleInfo.getId()).name("Virat Kohli").build();
        when(cricketDataApiClient.getPlayerInfo(anyString())).thenReturn(sampleInfo);
        when(playerRepository.findByExternalPlayerId(anyString())).thenReturn(Optional.of(player));
        when(playerRepository.save(any())).thenReturn(player);
        when(careerStatisticsRepository.findByPlayerId(1L)).thenReturn(Collections.emptyList());
        when(careerStatisticsRepository.saveAll(any())).thenAnswer(invocation -> new java.util.ArrayList<>(invocation.getArgument(0)));

        PlayerProfileResponse response = profileService.syncPlayer(sampleInfo.getId());

        assertThat(response.getCareerStatistics()).containsKey("T20I");
        CareerStatisticsDto t20i = response.getCareerStatistics().get("T20I");
        // t20i stat (4188) should take precedence over domestic t20 (12000)
        assertThat(t20i.getRuns()).isEqualTo(4188);
        assertThat(t20i.getHighestScore()).isEqualTo("122");
    }

    @Test
    void syncPlayer_missingOptionalFields_handlesGracefully() {
        CricketPlayerInfoData minimalInfo = CricketPlayerInfoData.builder()
                .id("minimal-id")
                .name("Test Minimal")
                .stats(Collections.emptyList())
                .build();

        Player player = Player.builder().id(2L).name("Test Minimal").build();
        when(cricketDataApiClient.getPlayerInfo("minimal-id")).thenReturn(minimalInfo);
        when(playerRepository.findByExternalPlayerId("minimal-id")).thenReturn(Optional.of(player));
        when(playerRepository.save(any())).thenReturn(player);
        when(careerStatisticsRepository.findByPlayerId(2L)).thenReturn(Collections.emptyList());

        PlayerProfileResponse response = profileService.syncPlayer("minimal-id");

        assertThat(response.getName()).isEqualTo("Test Minimal");
        assertThat(response.getDateOfBirth()).isNull();
        assertThat(response.getNationality()).isNull();
        assertThat(response.getCareerStatistics()).isEmpty();
    }

    @Test
    void getPlayerProfile_existingId_returnsProfileWithCareerStats() {
        Player player = Player.builder()
                .id(1L)
                .externalPlayerId("c61d247d-7f77-452c-b495-2813a9cd0ac4")
                .name("Virat Kohli")
                .build();
        PlayerCareerStatistics stat = PlayerCareerStatistics.builder()
                .id(100L)
                .player(player)
                .format("TEST")
                .runs(9230)
                .matches(123)
                .build();

        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(careerStatisticsRepository.findByPlayerId(1L)).thenReturn(List.of(stat));

        PlayerProfileResponse response = profileService.getPlayerProfile(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCareerStatistics()).containsKey("TEST");
        assertThat(response.getCareerStatistics().get("TEST").getRuns()).isEqualTo(9230);
    }

    @Test
    void getPlayerProfile_nonExistingId_throwsResourceNotFoundException() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getPlayerProfile(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}
