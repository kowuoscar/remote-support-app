package com.remotesupport.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The caller's own Login is deactivated, so their session is no longer valid. Maps to {@code 401}
 * (not {@link ChangePasswordRefusedException}'s 400): the frontend reads 401 as a session to end.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class CallerLoginDeactivatedException extends RuntimeException {

  public CallerLoginDeactivatedException() {
    super("Login is deactivated");
  }
}
