package com.remotesupport.backend.dto;

import java.util.UUID;

/**
 * Sets, moves or clears a SIM Card's Installed-in Smartphone from the Fleet page
 * (sim-installed-in-smartphone ticket AC: "the Agent (own Contract) or the Manager can set or
 * clear a SIM Card's Smartphone from the Fleet page"). A missing or {@code null} {@code
 * smartphoneId} clears the link back to none, mirroring {@code SmartphoneSerialUpdateRequest}'s
 * "absent clears it" shape.
 */
public record SimCardInstalledInUpdateRequest(UUID smartphoneId) {}
