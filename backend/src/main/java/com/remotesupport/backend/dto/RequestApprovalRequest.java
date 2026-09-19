package com.remotesupport.backend.dto;

/**
 * The Manager's approval body — empty for every type this ticket covers (Provision
 * Smartphone/SIM, Replace Smartphone/SIM all approve with no further input). Kept as its own type
 * rather than approving with no body at all, so a future type-specific approval input has
 * somewhere to go without changing the endpoint's shape: {@code returns-and-agent-stock}'s Return
 * Request will need the Manager to choose a Disposition per unit at the moment of approval
 * (CONTEXT.md "Disposition") — that ticket adds the field here, not a new endpoint or a breaking
 * change to this one. {@code RequestByIdController#approve} accepts this as optional
 * ({@code required = false}), so a caller sending no body (every type today) keeps working
 * unchanged once a future type does need one.
 */
public record RequestApprovalRequest() {}
