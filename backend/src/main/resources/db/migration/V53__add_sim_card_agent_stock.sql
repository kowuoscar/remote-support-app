-- agent-stock ticket: the SIM Card side of V52's own change -- a SIM Card kept in Stock leaves its
-- Contract and is held by the Contract's own Agent instead (spec.md Solution's Agent Stock).
ALTER TABLE sim_cards ALTER COLUMN contract_id DROP NOT NULL;
ALTER TABLE sim_cards ADD COLUMN holding_agent_id UUID REFERENCES agents (id);
ALTER TABLE sim_cards ADD CONSTRAINT chk_sim_cards_exactly_one_place CHECK (
    (contract_id IS NOT NULL AND holding_agent_id IS NULL)
    OR (contract_id IS NULL AND holding_agent_id IS NOT NULL)
);

CREATE INDEX idx_sim_cards_holding_agent_id ON sim_cards (holding_agent_id);
