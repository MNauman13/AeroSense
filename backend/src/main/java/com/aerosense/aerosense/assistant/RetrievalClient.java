package com.aerosense.aerosense.assistant;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class RetrievalClient {

  private final RestClient restClient;

  public RetrievalClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public AnswerResponse answer(RetrievalRequest request) {
    try {
      return restClient
          .post()
          .uri("/v1/answer")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(AnswerResponse.class);
    } catch (RestClientException exception) {
      throw new RetrievalUnavailableException(exception);
    }
  }
}
