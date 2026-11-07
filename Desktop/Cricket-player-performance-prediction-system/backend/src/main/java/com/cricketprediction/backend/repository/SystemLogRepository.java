package com.cricketprediction.backend.repository;

import com.cricketprediction.backend.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    /** Most recent log entries for a specific user. */
    List<SystemLog> findByUsernameOrderByTimestampDesc(String username);

    /** Most recent log entries for a specific action type. */
    List<SystemLog> findByActionOrderByTimestampDesc(String action);
}
