package com.remotesupport.backend.dto;

/**
 * Sets or changes a Smartphone's serial from the Fleet page (smartphone-owner-and-optional-serial
 * ticket AC: "the Agent (own Contract) or the Manager can set or change the serial later"). A
 * blank or missing {@code serial} clears it back to none.
 */
public record SmartphoneSerialUpdateRequest(String serial) {}
