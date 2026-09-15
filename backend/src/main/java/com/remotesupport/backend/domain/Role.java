package com.remotesupport.backend.domain;

/**
 * The tenant-scoped role model (spec.md Access control). {@code SUPER_ADMIN} is reserved:
 * cross-tenant, with no dedicated screens or behaviour this iteration.
 */
public enum Role {
  MANAGER,
  AGENT,
  TESTER,
  SUPER_ADMIN
}
