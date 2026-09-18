-- A Postpaid SIM Card names the Postpaid Plan its monthly fee was copied from (carrier-catalog
-- spec, SIM Card changes; postpaid-sim-plan ticket). The fee itself stays on the SIM Card: a later
-- Plan price change never touches a SIM Card already on it, so no Client Invoice total moves.
--
-- Existing Postpaid SIM Cards keep their monthly fee and get no Plan -- the column is nullable for
-- exactly that reason, as carrier_id is for a SIM Card from before the catalog. Only a Prepaid SIM
-- is forbidden one.

ALTER TABLE sim_cards ADD COLUMN postpaid_plan_id UUID REFERENCES postpaid_plans (id);

CREATE INDEX idx_sim_cards_postpaid_plan_id ON sim_cards (postpaid_plan_id);

ALTER TABLE sim_cards ADD CONSTRAINT chk_sim_cards_plan_only_when_postpaid CHECK (
    postpaid_plan_id IS NULL OR flavor = 'POSTPAID'
);
