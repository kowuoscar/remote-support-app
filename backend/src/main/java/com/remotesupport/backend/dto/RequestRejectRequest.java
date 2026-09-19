package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The Company Manager's required reason for rejecting a Pending Approval Request
 * (request-types-and-flow spec, Manager approval: "reject with a reason"; manager-approves-requests
 * ticket AC: "rejects (to Rejected, reason required)"). Blank is refused the same way a blank
 * Other-Request description is (bean validation, {@code @NotBlank}), not trimmed-to-null like the
 * optional description field — a reason is never optional here.
 */
public record RequestRejectRequest(@NotBlank String reason) {}
