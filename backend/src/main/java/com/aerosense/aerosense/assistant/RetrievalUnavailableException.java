package com.aerosense.aerosense.assistant;

public class RetrievalUnavailableException extends RuntimeException {

  public RetrievalUnavailableException(Throwable cause) {
    super("Retrieval service is unavailable.", cause);
  }
}
