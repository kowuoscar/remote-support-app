package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The Agent's edit of one line of a draft Client Invoice (edit-client-invoice-lines spec, "Backend:
 * editing a line"). A line is addressed by its {@code kind} and {@code sourceId} (the SIM Card or
 * the Fee; {@code null} for a {@code BASE_AMOUNT} line), never by a row id, because an untouched
 * line of a never-sent draft has no row. Only the {@code amount} changes; zero is allowed, a
 * negative is not, and two decimals is the column's precision. Saving the line's computed amount
 * is the reset.
 */
public record ClientInvoiceLineEditRequest(
    @NotNull(message = "kind is required") ClientInvoiceLineKind kind,
    UUID sourceId,
    @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0", message = "amount must not be negative")
        @Digits(integer = 10, fraction = 2, message = "amount must have at most two decimals")
        BigDecimal amount) {}
