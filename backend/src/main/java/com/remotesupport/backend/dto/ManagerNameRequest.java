package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code PUT /api/me/name}. Blank and too-long names are judged by {@link
 * com.remotesupport.backend.domain.ManagerName}, which gives each its own code; only a missing
 * {@code name} fails here.
 */
public record ManagerNameRequest(@NotNull String name) {}
