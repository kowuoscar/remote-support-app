package com.remotesupport.backend.dto;

import java.util.UUID;

/**
 * Corrects a Postpaid SIM Card's plan. A missing {@code postpaidPlanId} is refused as {@code
 * PLAN_NOT_ON_CARRIER}, like a plan the SIM's Carrier does not sell.
 */
public record SimCardPostpaidPlanUpdateRequest(UUID postpaidPlanId) {}
