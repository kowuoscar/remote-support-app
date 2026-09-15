-- Smartphone and SIM Card, the Fleet a Contract owns (spec.md Solution: "Fleet: the Smartphones
-- and SIM Cards provisioned under one Contract"; fleet-management ticket). Fleet itself has no
-- separate table: both entities reference their owning Contract directly, since a Contract owns
-- exactly one Fleet and nothing about "the Fleet" needs its own identity beyond that.

CREATE TABLE smartphones (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    model VARCHAR(255) NOT NULL,
    serial VARCHAR(255) NOT NULL,
    assigned_to VARCHAR(255),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'IN_REPAIR', 'RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_smartphones_tenant_id ON smartphones (tenant_id);
CREATE INDEX idx_smartphones_contract_id ON smartphones (contract_id);

-- A Postpaid SIM carries a fixed monthly fee (in the Contract's currency); a Prepaid SIM does
-- not (spec.md Solution). Enforced at the schema level, not just in application code, so the
-- invariant holds regardless of write path.
CREATE TABLE sim_cards (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    number VARCHAR(64) NOT NULL,
    carrier VARCHAR(255),
    flavor VARCHAR(16) NOT NULL CHECK (flavor IN ('POSTPAID', 'PREPAID')),
    monthly_fee_amount NUMERIC(12, 2),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sim_cards_postpaid_has_fee CHECK (
        (flavor = 'POSTPAID' AND monthly_fee_amount IS NOT NULL)
        OR (flavor = 'PREPAID' AND monthly_fee_amount IS NULL)
    )
);

CREATE INDEX idx_sim_cards_tenant_id ON sim_cards (tenant_id);
CREATE INDEX idx_sim_cards_contract_id ON sim_cards (contract_id);
