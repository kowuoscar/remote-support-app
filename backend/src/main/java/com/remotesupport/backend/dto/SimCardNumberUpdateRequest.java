package com.remotesupport.backend.dto;

/**
 * Corrects a SIM Card's number. Trimmed and length-checked by the service; a missing or blank
 * {@code number} is refused as {@code NUMBER_BLANK}.
 */
public record SimCardNumberUpdateRequest(String number) {}
