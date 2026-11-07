package com.cricketprediction.backend.repository;

import com.cricketprediction.backend.model.Player;
import com.cricketprediction.backend.model.PlayerStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerStatisticsRepository extends JpaRepository<PlayerStatistics, Long> {

    /** Retrieve all statistics rows for a given player, ordered by match ID ascending. */
    List<PlayerStatistics> findByPlayerOrderByMatchIdAsc(Player player);
}
