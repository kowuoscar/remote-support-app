package com.remotesupport.backend.dto;

import java.util.UUID;

public record MeResponse(UUID userId, String username, UUID tenantId, String role) {}
