package com.aerosense.aerosense.analysis;

import com.aerosense.aerosense.assistant.RetrievalClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AnalyticsClientConfiguration {

  @Bean
  AnalyticsClient analyticsClient(
      RestClient.Builder builder, @Value("${aerosense.analytics-url}") String analyticsUrl) {
    return new AnalyticsClient(configuredClient(builder, analyticsUrl, Duration.ofSeconds(30)));
  }

  @Bean
  RetrievalClient retrievalClient(
      RestClient.Builder builder, @Value("${aerosense.retrieval-url}") String retrievalUrl) {
    return new RetrievalClient(configuredClient(builder, retrievalUrl, Duration.ofSeconds(10)));
  }

  private RestClient configuredClient(
      RestClient.Builder builder, String serviceUrl, Duration readTimeout) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return builder.baseUrl(serviceUrl).requestFactory(requestFactory).build();
  }
}
