package com.cricketprediction.backend.repository;

import com.cricketprediction.backend.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /** Check or retrieve a match by its external data-source identifier. */
    Optional<Match> findByExternalMatchId(String externalMatchId);

    boolean existsByExternalMatchId(String externalMatchId);
}
