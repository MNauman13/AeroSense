package com.aerosense.aerosense;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@OpenAPIDefinition(
    info =
        @Info(
            title = "AeroSense API",
            version = "1.0.0",
            description =
                "API for a fictional synthetic test-data demonstration. Not engineering or maintenance advice."))
public class AeroSenseApplication {

  public static void main(String[] args) {
    SpringApplication.run(AeroSenseApplication.class, args);
  }

  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
