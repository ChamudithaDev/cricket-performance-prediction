package com.cricketprediction.backend.repository;

import com.cricketprediction.backend.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    /** Find by exact name (case-sensitive, as stored). */
    Optional<Player> findByName(String name);

    /** Find by external Cricket Data API identifier. */
    Optional<Player> findByExternalPlayerId(String externalPlayerId);

    /** Find all players on a given team. */
    List<Player> findByCurrentTeamIgnoreCase(String currentTeam);

    /**
     * Case-insensitive partial-name search.
     * E.g. "kohli" matches "V Kohli", "Virat Kohli".
     */
    List<Player> findByNameContainingIgnoreCase(String name);
}
