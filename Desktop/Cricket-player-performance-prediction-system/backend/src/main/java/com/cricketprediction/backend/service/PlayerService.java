package com.cricketprediction.backend.service;

import com.cricketprediction.backend.dto.PlayerResponse;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import com.cricketprediction.backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the Player Management API.
 *
 * All methods are read-only (Transactional(readOnly = true)) because
 * this task only exposes GET endpoints. Write operations will be added
 * in a separate admin task.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;

    /**
     * Return all players sorted by name ascending.
     * Pagination can be layered on in a future iteration once the
     * Spring Data Web Jackson module is included in the project.
     *
     * @return list of PlayerResponse DTOs sorted by name
     */
    public List<PlayerResponse> getAllPlayers() {
        return playerRepository.findAll(Sort.by("name").ascending())
                .stream()
                .map(PlayerResponse::from)
                .toList();
    }

    /**
     * Return a single player by primary-key ID.
     *
     * @param id player primary key
     * @return PlayerResponse DTO
     * @throws ResourceNotFoundException if no player with this ID exists
     */
    public PlayerResponse getPlayerById(Long id) {
        return playerRepository.findById(id)
                .map(PlayerResponse::from)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Player not found with id: " + id));
    }

    /**
     * Case-insensitive partial-name search.
     * An empty or blank query returns an empty list (not all players).
     *
     * @param name partial player name, e.g. "Kohli"
     * @return list of matching PlayerResponse DTOs
     */
    public List<PlayerResponse> searchByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        return playerRepository.findByNameContainingIgnoreCase(name.trim())
                .stream()
                .map(PlayerResponse::from)
                .toList();
    }
}
