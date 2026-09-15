-- Client Invoice send/approve lifecycle (client-invoice-submission-and-visibility ticket;
-- CONTEXT.md "Client Invoice" snapshot-on-send decision).
--
-- `sent_at`/`approved_at` record when each transition happened (nullable -- both are null for a
-- DRAFT, sent_at is set from SENT onward, approved_at only once APPROVED), mirroring the
-- Observability requirement to log these transitions with a timestamp.
--
-- `snapshot_base_amount` freezes the base-amount figure the Agent actually sent, the moment they
-- sent it. It stays NULL for a DRAFT (still computed live -- see ClientInvoiceController) and is
-- populated exactly once, when the DRAFT -> SENT transition happens, never touched again
-- afterwards (APPROVED never re-snapshots). See ClientInvoice's Javadoc for why a live-computed
-- view is wrong once an invoice has been shown to a Manager/Client as final.
ALTER TABLE client_invoices
    ADD COLUMN sent_at TIMESTAMPTZ,
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN snapshot_base_amount NUMERIC(12, 2);

-- The frozen set of Fee lines a SENT/APPROVED Client Invoice counts. Fee rows themselves are
-- never updated or deleted once created (fee-logging-and-provisioning ticket: Fee has no PATCH/
-- DELETE endpoint), so freezing "which Fees counted" is exactly the same as freezing their
-- values -- this table only needs to pin the *membership* of the set, not duplicate every Fee
-- column. A Fee logged against this Contract/month *after* sending is real and stays queryable
-- everywhere else, it simply never gets a row here, so it can never silently change a sent/
-- approved invoice's total.
CREATE TABLE client_invoice_fee_snapshots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    client_invoice_id UUID NOT NULL REFERENCES client_invoices (id),
    fee_id UUID NOT NULL REFERENCES fees (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_civ_fee_snapshots_invoice_fee UNIQUE (client_invoice_id, fee_id)
);

CREATE INDEX idx_civ_fee_snapshots_tenant_id ON client_invoice_fee_snapshots (tenant_id);
CREATE INDEX idx_civ_fee_snapshots_client_invoice_id ON client_invoice_fee_snapshots (client_invoice_id);
