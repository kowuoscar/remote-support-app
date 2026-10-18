package com.remotesupport.backend.dto;

import java.time.LocalDate;

/** One billing month of an Agent for the Manager's Month select, with what waits in it. */
public record ReviewMonthSummaryResponse(LocalDate billingMonth, long waiting) {}
