package com.cricketprediction.backend.repository;

import com.cricketprediction.backend.model.Prediction;
import com.cricketprediction.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /** All predictions made by a specific user, most recent first. */
    List<Prediction> findByRequestedByOrderByPredictionDateDesc(User user);

    /** Top 20 predictions made by a specific user, most recent first. */
    List<Prediction> findTop20ByRequestedByOrderByPredictionDateDesc(User user);

    /** Top 20 predictions across system, most recent first. */
    List<Prediction> findTop20ByOrderByPredictionDateDesc();

    /** All predictions for a given player name. */
    List<Prediction> findByPlayerNameOrderByPredictionDateDesc(String playerName);
}
