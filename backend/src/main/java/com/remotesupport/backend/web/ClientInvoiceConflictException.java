package com.remotesupport.backend.web;

import java.util.List;
import java.util.UUID;

/**
 * A 409 on a Client Invoice that carries which cause applies, so the client can tell it from the
 * plain "wrong status" conflict. {@link ClientInvoiceByIdController} and {@link
 * ClientInvoiceController} each render it as {@code {code, message}}, as {@link AgentController}
 * does for {@link AgentLoginConflictException}.
 */
public class ClientInvoiceConflictException extends ConflictException {

  /** The machine-readable cause, sent to the client as {@code code}. */
  public enum Reason {
    /** A draft of a past billing month that was never sent: it could only be built from today's Fleet. */
    PAST_MONTH_DRAFT_NOT_SENDABLE,
    /** The SIM Card already has its Carrier Invoice File on this invoice. */
    CARRIER_INVOICE_ALREADY_ATTACHED,
    /** A Postpaid SIM line of the invoice has no Carrier Invoice File, so it cannot be sent. */
    CARRIER_INVOICES_MISSING,
    /** A never-sent invoice cannot be sent on its own while its Agent's month is not sent. */
    MONTH_NOT_SENT,
    /** The Agent's month was already sent (some invoice of it has been sent). */
    MONTH_ALREADY_SENT,
    /** The invoices the caller saw are not the invoices the Agent's month holds. */
    MONTH_CHANGED,
    /** The month send is for the current UTC month only. */
    NOT_THE_CURRENT_MONTH,
    /** The Agent holds no Contract, so there is no month to send. */
    NOTHING_TO_SEND
  }

  private final Reason reason;
  private final List<UUID> invoiceIds;

  public ClientInvoiceConflictException(Reason reason, String message) {
    this(reason, message, null);
  }

  /** A refusal that names the invoices it is about ({@code invoiceIds} in the body). */
  public ClientInvoiceConflictException(Reason reason, String message, List<UUID> invoiceIds) {
    super(message);
    this.reason = reason;
    this.invoiceIds = invoiceIds == null ? null : List.copyOf(invoiceIds);
  }

  /** The invoices the refusal names, or {@code null} when it names none. */
  public List<UUID> invoiceIds() {
    return invoiceIds;
  }

  public Reason reason() {
    return reason;
  }
}
