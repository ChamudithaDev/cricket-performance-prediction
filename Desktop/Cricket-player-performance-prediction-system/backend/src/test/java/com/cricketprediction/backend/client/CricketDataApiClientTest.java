package com.cricketprediction.backend.client;

import com.cricketprediction.backend.dto.external.CricketPlayerInfoData;
import com.cricketprediction.backend.dto.external.CricketPlayerSearchItem;
import com.cricketprediction.backend.exception.ExternalApiException;
import com.cricketprediction.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CricketDataApiClientTest {

    private MockRestServiceServer mockServer;
    private CricketDataApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.cricapi.com/v1");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new CricketDataApiClient(builder.build(), "dummy-test-key");
    }

    @Test
    void searchPlayers_successfulResponse_returnsItemList() {
        String json = """
                {
                  "apikey": "dummy-test-key",
                  "status": "success",
                  "data": [
                    {
                      "id": "c61d247d-7f77-452c-b495-2813a9cd0ac4",
                      "name": "Virat Kohli",
                      "country": "India"
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.cricapi.com/v1/players?apikey=dummy-test-key&offset=0&search=Virat"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<CricketPlayerSearchItem> results = client.searchPlayers("Virat");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("c61d247d-7f77-452c-b495-2813a9cd0ac4");
        assertThat(results.get(0).getName()).isEqualTo("Virat Kohli");
        assertThat(results.get(0).getCountry()).isEqualTo("India");
        mockServer.verify();
    }

    @Test
    void searchPlayers_blankQuery_returnsEmptyWithoutNetworkCall() {
        List<CricketPlayerSearchItem> results = client.searchPlayers("   ");
        assertThat(results).isEmpty();
        mockServer.verify();
    }

    @Test
    void searchPlayers_missingApiKey_throwsExternalApiException() {
        CricketDataApiClient clientNoKey = new CricketDataApiClient(RestClient.builder().build(), "");
        assertThatThrownBy(() -> clientNoKey.searchPlayers("Kohli"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("key is not configured");
    }

    @Test
    void searchPlayers_apiFailureStatus_throwsExternalApiException() {
        String json = """
                {
                  "apikey": "dummy-test-key",
                  "status": "failure"
                }
                """;

        mockServer.expect(requestTo("https://api.cricapi.com/v1/players?apikey=dummy-test-key&offset=0&search=Kohli"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchPlayers("Kohli"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("failure");
    }

    @Test
    void getPlayerInfo_successfulResponse_returnsData() {
        String json = """
                {
                  "apikey": "dummy-test-key",
                  "status": "success",
                  "data": {
                    "id": "c61d247d-7f77-452c-b495-2813a9cd0ac4",
                    "name": "Virat Kohli",
                    "dateOfBirth": "1988-11-05T00:00:00",
                    "role": "Batsman",
                    "battingStyle": "Right Handed Bat",
                    "bowlingStyle": "Right-arm medium",
                    "placeOfBirth": "Delhi",
                    "country": "India",
                    "playerImg": "https://example.com/kohli.jpg",
                    "stats": [
                      {
                        "fn": "batting",
                        "matchtype": "odi",
                        "stat": "runs",
                        "value": "14181"
                      }
                    ]
                  }
                }
                """;

        mockServer.expect(requestTo("https://api.cricapi.com/v1/players_info?apikey=dummy-test-key&id=c61d247d-7f77-452c-b495-2813a9cd0ac4"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        CricketPlayerInfoData data = client.getPlayerInfo("c61d247d-7f77-452c-b495-2813a9cd0ac4");

        assertThat(data).isNotNull();
        assertThat(data.getId()).isEqualTo("c61d247d-7f77-452c-b495-2813a9cd0ac4");
        assertThat(data.getName()).isEqualTo("Virat Kohli");
        assertThat(data.getStats()).hasSize(1);
        assertThat(data.getStats().get(0).getValue()).isEqualTo("14181");
        mockServer.verify();
    }

    @Test
    void getPlayerInfo_notFoundOrEmptyData_throwsResourceNotFoundException() {
        String json = """
                {
                  "apikey": "dummy-test-key",
                  "status": "success",
                  "data": null
                }
                """;

        mockServer.expect(requestTo("https://api.cricapi.com/v1/players_info?apikey=dummy-test-key&id=invalid-id"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getPlayerInfo("invalid-id"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getPlayerInfo_http500_throwsExternalApiException() {
        mockServer.expect(requestTo("https://api.cricapi.com/v1/players_info?apikey=dummy-test-key&id=error-id"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getPlayerInfo("error-id"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Cricket Data API player info request failed");
    }

    @Test
    void getPlayerInfo_missingApiKey_throwsExternalApiException() {
        CricketDataApiClient clientNoKey = new CricketDataApiClient(RestClient.builder().build(), "");
        assertThatThrownBy(() -> clientNoKey.getPlayerInfo("some-id"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("key is not configured");
    }

    @Test
    void searchPlayers_errorSanitization_redactsApiKey() {
        mockServer.expect(requestTo("https://api.cricapi.com/v1/players?apikey=dummy-test-key&offset=0&search=ErrorPlayer"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.searchPlayers("ErrorPlayer"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageNotContaining("dummy-test-key");
    }

    @Test
    void contextRunner_cricketDataApiClientBean_startsSuccessfullyWithoutApiKey() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(CricketDataApiClient.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CricketDataApiClient.class);
                    CricketDataApiClient bean = context.getBean(CricketDataApiClient.class);
                    assertThatThrownBy(() -> bean.searchPlayers("Test"))
                            .isInstanceOf(ExternalApiException.class)
                            .hasMessageContaining("Cricket Data API key is not configured");
                });
    }

    @Test
    void contextRunner_cricketDataApiClientBean_startsSuccessfullyWithApiKey() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(CricketDataApiClient.class)
                .withPropertyValues("cricket.api.key=some-api-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CricketDataApiClient.class);
                });
    }
}
