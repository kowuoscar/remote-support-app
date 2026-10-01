package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Country;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * An Agent and its login, created together (create-agent-with-login ticket). The password is
 * generated and returned once; a body that still sends {@code password} has it ignored like any
 * unknown property.
 */
public record AgentCreateRequest(
    @NotBlank String name,
    @NotNull Country country,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal salaryAmount,
    @NotBlank String username) {}
