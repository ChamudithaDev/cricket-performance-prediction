package com.cricketprediction.backend.client;

import com.cricketprediction.backend.dto.external.CricketPlayerInfoData;
import com.cricketprediction.backend.dto.external.CricketPlayerInfoResponse;
import com.cricketprediction.backend.dto.external.CricketPlayerSearchItem;
import com.cricketprediction.backend.dto.external.CricketPlayerSearchResponse;
import com.cricketprediction.backend.exception.ExternalApiException;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;


@Slf4j
@Component
public class CricketDataApiClient {

    private final RestClient restClient;
    private final String apiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public CricketDataApiClient(
            @Value("${cricket.api.base-url:https://api.cricapi.com/v1}") String baseUrl,
            @Value("${cricket.api.key:}") String apiKey) {
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(6));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Constructor for unit testing with custom/mock RestClient. */
    CricketDataApiClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
    }

    private void ensureApiKeyConfigured() {
        if (this.apiKey.isBlank()) {
            throw new ExternalApiException("Cricket Data API key is not configured. Please set the CRICKET_API_KEY environment variable.");
        }
    }

    /**
     * Search players by partial or full name via GET /players.
     *
     * @param search player name query
     * @return list of search matches
     */
    public List<CricketPlayerSearchItem> searchPlayers(String search) {
        if (search == null || search.isBlank()) {
            return Collections.emptyList();
        }
        ensureApiKeyConfigured();

        try {
            log.info("Searching external players for query: {}", sanitizeLogInput(search));
            CricketPlayerSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/players")
                            .queryParam("apikey", apiKey)
                            .queryParam("offset", 0)
                            .queryParam("search", search.trim())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CricketPlayerSearchResponse.class);

            if (response == null) {
                throw new ExternalApiException("Received empty response from Cricket Data API during player search.");
            }

            if ("failure".equalsIgnoreCase(response.getStatus())) {
                throw new ExternalApiException("Cricket Data API returned failure status during player search.");
            }

            return response.getData() != null ? response.getData() : Collections.emptyList();
        } catch (RestClientException ex) {
            log.error("Failed to execute external player search: {}", sanitizeErrorMessage(ex.getMessage()));
            throw new ExternalApiException("Cricket Data API search request failed: " + sanitizeErrorMessage(ex.getMessage()), ex);
        }
    }

    /**
     * Fetch complete player profile and career statistics via GET /players_info.
     *
     * @param externalPlayerId external UUID (e.g. "c61d247d-7f77-452c-b495-2813a9cd0ac4")
     * @return raw CricketPlayerInfoData
     */
    public CricketPlayerInfoData getPlayerInfo(String externalPlayerId) {
        if (externalPlayerId == null || externalPlayerId.isBlank()) {
            throw new IllegalArgumentException("External player ID must not be blank.");
        }
        ensureApiKeyConfigured();

        try {
            log.info("Fetching external player info for id: {}", sanitizeLogInput(externalPlayerId));
            CricketPlayerInfoResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/players_info")
                            .queryParam("apikey", apiKey)
                            .queryParam("id", externalPlayerId.trim())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CricketPlayerInfoResponse.class);

            if (response == null) {
                throw new ExternalApiException("Received empty response from Cricket Data API for player: " + externalPlayerId);
            }

            if ("failure".equalsIgnoreCase(response.getStatus())) {
                throw new ExternalApiException("Cricket Data API returned failure status for player: " + externalPlayerId);
            }

            if (response.getData() == null || response.getData().getId() == null) {
                throw new ResourceNotFoundException("External player not found with id: " + externalPlayerId);
            }

            return response.getData();
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Failed to fetch external player info: {}", sanitizeErrorMessage(ex.getMessage()));
            throw new ExternalApiException("Cricket Data API player info request failed: " + sanitizeErrorMessage(ex.getMessage()), ex);
        }
    }

    private String sanitizeLogInput(String input) {
        return input.replaceAll("[\r\n]", "_");
    }

    private String sanitizeErrorMessage(String msg) {
        if (msg == null) return "Unknown error";
        if (!apiKey.isBlank()) {
            msg = msg.replace(apiKey, "[REDACTED]");
        }
        return msg;
    }
}
