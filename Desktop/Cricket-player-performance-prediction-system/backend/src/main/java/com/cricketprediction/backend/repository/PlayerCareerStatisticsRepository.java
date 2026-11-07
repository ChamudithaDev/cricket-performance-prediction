package com.cricketprediction.backend.repository;

import com.cricketprediction.backend.model.PlayerCareerStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerCareerStatisticsRepository extends JpaRepository<PlayerCareerStatistics, Long> {

    List<PlayerCareerStatistics> findByPlayerId(Long playerId);

    Optional<PlayerCareerStatistics> findByPlayerIdAndFormat(Long playerId, String format);
}
