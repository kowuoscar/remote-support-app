package com.remotesupport.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The object store holding Carrier Invoice File bytes failed or cannot be used: 503. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ObjectStoreUnavailableException extends RuntimeException {

  public ObjectStoreUnavailableException(String message) {
    super(message);
  }

  public ObjectStoreUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
