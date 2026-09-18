package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SmartphoneOwner;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code serial} is optional (smartphone-owner-and-optional-serial ticket AC: "A Smartphone can
 * be created without a serial"). {@code owner} defaults to {@link SmartphoneOwner#COMPANY} when
 * absent -- the Manager's add-Smartphone form always sends its own selection, defaulting to
 * company. The provisioning side-effect path ({@link com.remotesupport.backend.web.ProvisioningService})
 * ignores whatever this carries and always sets {@code COMPANY}: a Smartphone reached through a
 * Provision or Replace Request is always company-owned, never a Manager's choice.
 */
public record SmartphoneCreateRequest(@NotBlank String model, String serial, SmartphoneOwner owner) {}
