package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * {@code agentId}/{@code clientId} are the resolved link from an AGENT/TESTER-role login to the
 * Agent/Client record it corresponds to (null, and omitted, for a MANAGER or an unlinked login) —
 * see {@link com.remotesupport.backend.domain.User#getAgent()} and
 * {@link com.remotesupport.backend.domain.Tester}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(
    UUID userId, String username, UUID tenantId, String role, UUID agentId, UUID clientId) {}
