package com.remotesupport.backend.web;

/** A 409: the Agent exists but has no Login to reset, so the page showing a reset is stale. */
public class AgentHasNoLoginException extends ConflictException {

  public AgentHasNoLoginException(String message) {
    super(message);
  }
}
