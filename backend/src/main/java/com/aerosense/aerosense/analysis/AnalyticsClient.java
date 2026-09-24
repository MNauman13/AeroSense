package com.aerosense.aerosense.analysis;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class AnalyticsClient {

  private final RestClient restClient;

  public AnalyticsClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public ScoreResponse score(ScoreRequest request) {
    try {
      return restClient
          .post()
          .uri("/v1/score")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(ScoreResponse.class);
    } catch (RestClientException exception) {
      throw new AnalyticsUnavailableException(exception);
    }
  }

  public EvaluationResponse evaluate(EvaluationRequest request) {
    try {
      return restClient
          .post()
          .uri("/v1/evaluate")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(EvaluationResponse.class);
    } catch (RestClientException exception) {
      throw new AnalyticsUnavailableException(exception);
    }
  }
}
