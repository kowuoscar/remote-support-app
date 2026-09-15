-- Fee: a billable line item an Agent logs against a Contract, always linked to the Request that
-- caused it (spec.md Solution; fee-logging-and-provisioning ticket). `request_id` is NOT NULL and
-- has no other write path into this table (see FeeApiTest's direct-repository test) — that's the
-- "no way to create an untraceable Fee" acceptance criterion enforced at the schema level, not
-- just in application code.
--
-- `contract_id` is denormalized from `requests.contract_id` (same pattern as Smartphone/SIM
-- Card/Request all storing their owning Contract directly) so a monthly Fee total for a Contract
-- is a plain filter, no join required.
--
-- `billing_month` is stored explicitly (first day of the month) rather than derived from
-- `created_at` at query time -- see Fee.java's Javadoc for why.
CREATE TABLE fees (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    request_id UUID NOT NULL REFERENCES requests (id),
    fee_type VARCHAR(32) NOT NULL CHECK (
        fee_type IN ('TOPUP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM', 'REPAIR')
    ),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL,
    description TEXT,
    billing_month DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fees_tenant_id ON fees (tenant_id);
CREATE INDEX idx_fees_contract_id ON fees (contract_id);
CREATE INDEX idx_fees_request_id ON fees (request_id);
-- client-invoice-generation ticket: "every Fee logged against the Contract this month" is always
-- filtered by exactly these two columns together.
CREATE INDEX idx_fees_contract_id_billing_month ON fees (contract_id, billing_month);
