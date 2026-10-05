package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The Manager's required reason for sending a sent Client Invoice back to draft
 * (send-a-client-invoice-back spec, "Backend: send back"). Blank or over 1000 characters is a 400.
 */
public record ClientInvoiceSendBackRequest(@NotBlank @Size(max = 1000) String reason) {}
