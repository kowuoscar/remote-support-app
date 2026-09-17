package com.remotesupport.backend.repository;

import java.util.UUID;

/** The username an Agent signs in with, projected without loading the User or the Agent. */
public record AgentLogin(UUID agentId, String username) {}
