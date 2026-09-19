package com.remotesupport.backend.domain;

/**
 * What happens to one unit named on a {@link RequestType#RETURN} Request (spec.md Solution's
 * Disposition table; CONTEXT.md "Disposition"). Every value is declared now even though this
 * ticket (return-client-owned-smartphones) only ever writes {@link #POSTED_TO_CLIENT} — fixed at
 * submission, since a company-owned unit is refused for now — mirroring how {@link RequestStatus}
 * declared {@code PENDING_APPROVAL}/{@code REJECTED} ahead of the ticket that first wrote them.
 * {@link #POSTED_TO_COMPANY} and {@link #CANCELLED} are chosen by the Company Manager at approval
 * (manager-decides-return-disposition ticket); {@link #KEPT_IN_STOCK} is chosen the same way, for
 * either a company-owned Smartphone or a SIM Card (agent-stock ticket).
 */
public enum Disposition {
  POSTED_TO_CLIENT,
  POSTED_TO_COMPANY,
  CANCELLED,
  KEPT_IN_STOCK
}
