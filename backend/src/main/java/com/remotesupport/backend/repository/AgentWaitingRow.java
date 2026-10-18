package com.remotesupport.backend.repository;

import java.util.UUID;

/** How many of an Agent's invoices of one kind are waiting on the Manager (review by Agent). */
public record AgentWaitingRow(UUID agentId, Long count) {}
