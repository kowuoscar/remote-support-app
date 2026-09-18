package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Country;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new Carrier. {@code country} may be omitted by an Agent, whose catalog is always their own
 * Country's; the Company Manager must name one.
 */
public record CarrierCreateRequest(Country country, @NotBlank @Size(max = 255) String name) {}
