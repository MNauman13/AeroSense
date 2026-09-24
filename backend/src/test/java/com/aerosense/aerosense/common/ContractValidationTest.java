package com.aerosense.aerosense.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.aerosense.aerosense.assistant.QuestionRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class ContractValidationTest {

  @Test
  void questionMustBePresentAndNoLongerThanFiveHundredCharacters() {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      var validator = validatorFactory.getValidator();

      assertThat(validator.validate(new QuestionRequest("   ", null))).isNotEmpty();
      assertThat(validator.validate(new QuestionRequest("q".repeat(501), null))).isNotEmpty();
      assertThat(
              validator.validate(
                  new QuestionRequest("Why was this synthetic cycle flagged?", null)))
          .isEmpty();
    }
  }
}
