-- Client Invoice: one per Contract per calendar month (spec.md Solution; client-invoice-
-- generation ticket). Base amount and Fee lines are never stored here -- both are computed live
-- on every read (base amount from currently-Active Postpaid sim_cards, Fee lines from fees.
-- contract_id + billing_month) -- see ClientInvoiceController's Javadoc for why.
--
-- `status` is declared wide enough for the whole spec.md lifecycle (draft -> sent -> approved)
-- even though this ticket only ever writes DRAFT -- the client-invoice-submission-and-visibility
-- ticket needs SENT/APPROVED without a schema change, mirroring how RequestStatus was
-- pre-declared with values tester-request-submission didn't write yet.
--
-- The unique (contract_id, billing_month) pair is what makes "get or create this month's draft"
-- safe under concurrent first access: a second racing insert is rejected by this constraint, not
-- by application-level locking (see ClientInvoiceController's race-handling note).
CREATE TABLE client_invoices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    billing_month DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'APPROVED')),
    currency VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_client_invoices_contract_billing_month UNIQUE (contract_id, billing_month)
);

CREATE INDEX idx_client_invoices_tenant_id ON client_invoices (tenant_id);
CREATE INDEX idx_client_invoices_contract_id ON client_invoices (contract_id);
