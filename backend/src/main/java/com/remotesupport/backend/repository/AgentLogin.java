package com.remotesupport.backend.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * The username an Agent signs in with and when that Login was deactivated ({@code null} while
 * active), projected without loading the User or the Agent.
 */
public record AgentLogin(UUID agentId, String username, Instant deactivatedAt) {}
