package com.aerosense.aerosense.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void malformedBodyUsesSafeCommonErrorShape() {
    var response =
        handler.handleUnreadableBody(
            new HttpMessageNotReadableException("private parser detail must not be returned"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_JSON");
    assertThat(response.getBody().message()).isEqualTo("The request body is missing or malformed.");
    assertThat(response.getBody().message()).doesNotContain("private parser detail");
    assertThat(UUID.fromString(response.getBody().requestId())).isNotNull();
  }
}
