package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Country;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AgentCreateRequest(
    @NotBlank String name,
    @NotNull Country country,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal salaryAmount) {}
