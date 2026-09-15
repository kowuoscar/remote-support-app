package com.remotesupport.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A request conflicts with existing state (e.g. a Client already has a primary contact). */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
