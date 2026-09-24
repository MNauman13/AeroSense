package com.aerosense.aerosense.analysis;

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
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(30));
    RestClient restClient = builder.baseUrl(analyticsUrl).requestFactory(requestFactory).build();
    return new AnalyticsClient(restClient);
  }
}
