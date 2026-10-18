package com.remotesupport.backend.dto;

import java.util.UUID;

/** An Agent as the Manager's review side list shows it: how many of their invoices wait on the Manager. */
public record ReviewAgentResponse(UUID agentId, String agentName, long waiting) {}
