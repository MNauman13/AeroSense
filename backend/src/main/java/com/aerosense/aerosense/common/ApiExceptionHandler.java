package com.aerosense.aerosense.common;

import com.aerosense.aerosense.analysis.AnalyticsUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleBadArgument(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(TypeMismatchException.class)
  public ResponseEntity<ApiError> handleTypeMismatch(TypeMismatchException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_PARAMETER",
        "A query or path parameter has an invalid format.",
        Map.of());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> handleStatus(ResponseStatusException exception) {
    HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
    String code =
        switch (status) {
          case NOT_FOUND -> "NOT_FOUND";
          case CONFLICT -> "CONFLICT";
          default -> "REQUEST_FAILED";
        };
    String message =
        exception.getReason() == null
            ? "The request could not be completed."
            : exception.getReason();
    return error(status, code, message, Map.of());
  }

  @ExceptionHandler(AnalyticsUnavailableException.class)
  public ResponseEntity<ApiError> handleAnalyticsUnavailable(
      AnalyticsUnavailableException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "ANALYTICS_UNAVAILABLE",
        "Synthetic analytics is temporarily unavailable.",
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
