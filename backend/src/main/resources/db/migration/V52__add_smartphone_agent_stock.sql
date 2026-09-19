-- agent-stock ticket: a Smartphone kept in Stock (Disposition KEPT_IN_STOCK, chosen by the
-- Manager at approval) leaves its Contract and is held by the Contract's own Agent instead
-- (spec.md Solution's Agent Stock: "A unit in Stock belongs to no Contract and is held by exactly
-- one Agent"). contract_id becomes nullable and a new holding_agent_id is added; a CHECK enforces
-- exactly one of the two is set at all times (CONTEXT.md "Agent Stock": "a unit is in exactly one
-- place"), mirroring returned_units' own exactly-one-unit CHECK from V46.
ALTER TABLE smartphones ALTER COLUMN contract_id DROP NOT NULL;
ALTER TABLE smartphones ADD COLUMN holding_agent_id UUID REFERENCES agents (id);
ALTER TABLE smartphones ADD CONSTRAINT chk_smartphones_exactly_one_place CHECK (
    (contract_id IS NOT NULL AND holding_agent_id IS NULL)
    OR (contract_id IS NULL AND holding_agent_id IS NOT NULL)
);

CREATE INDEX idx_smartphones_holding_agent_id ON smartphones (holding_agent_id);
