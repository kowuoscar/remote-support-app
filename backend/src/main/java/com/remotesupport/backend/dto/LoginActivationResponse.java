package com.remotesupport.backend.dto;

import java.time.Instant;

/**
 * The {@code 200} body of the Login deactivate and reactivate routes: when the Login was switched
 * off, or {@code null} while it is active (deactivate-a-login spec, "Endpoints").
 */
public record LoginActivationResponse(Instant deactivatedAt) {}
