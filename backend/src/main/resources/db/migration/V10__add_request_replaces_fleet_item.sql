-- Which existing Fleet unit (if any) a Provision Smartphone/SIM Request retires when it completes
-- (fee-logging-and-provisioning ticket -- the schema gap tester-request-submission deliberately
-- left open; see Request.java's Javadoc). Nullable: a first-time provisioning retires nothing.
-- At most one of the two is ever set -- a Request's own `type` already says which Fleet table (if
-- either) is relevant, so both being set at once would always be a bug, not a valid state.
ALTER TABLE requests ADD COLUMN replaces_smartphone_id UUID REFERENCES smartphones (id);
ALTER TABLE requests ADD COLUMN replaces_sim_card_id UUID REFERENCES sim_cards (id);

ALTER TABLE requests ADD CONSTRAINT chk_requests_replaces_at_most_one CHECK (
    replaces_smartphone_id IS NULL OR replaces_sim_card_id IS NULL
);
