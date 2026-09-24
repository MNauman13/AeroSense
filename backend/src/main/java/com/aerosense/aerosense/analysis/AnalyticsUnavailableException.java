package com.aerosense.aerosense.analysis;

public class AnalyticsUnavailableException extends RuntimeException {

  public AnalyticsUnavailableException(Throwable cause) {
    super("Analytics service is unavailable.", cause);
  }
}
