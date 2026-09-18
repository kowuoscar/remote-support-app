package com.remotesupport.backend.domain;

/**
 * Who owns a Smartphone (spec.md Solution — Fleet model: "Smartphone gains an Owner, Client or
 * company"; smartphone-owner-and-optional-serial ticket AC: "A Smartphone has an Owner, Client or
 * company"). A SIM Card is always company-owned and carries no such field. See CONTEXT.md
 * "Owner".
 */
public enum SmartphoneOwner {
  CLIENT,
  COMPANY
}
