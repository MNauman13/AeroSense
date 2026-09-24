package com.aerosense.aerosense.common;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, List<String>> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.groupingBy(
                    FieldError::getField,
                    Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
    return error(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", fieldErrors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(
      ConstraintViolationException exception) {
    Map<String, List<String>> fieldErrors =
        exception.getConstraintViolations().stream()
            .collect(
                Collectors.groupingBy(
                    violation -> violation.getPropertyPath().toString(),
                    Collectors.mapping(violation -> violation.getMessage(), Collectors.toList())));
    return error(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_JSON",
        "The request body is missing or malformed.",
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "The request could not be completed.",
        Map.of());
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status, String code, String message, Map<String, List<String>> fieldErrors) {
    return ResponseEntity.status(status)
        .body(new ApiError(code, message, UUID.randomUUID().toString(), fieldErrors));
  }
}
