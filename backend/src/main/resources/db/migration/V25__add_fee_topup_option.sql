-- A Topup Fee can name the Topup Option it was bought from (carrier-catalog spec, Fee changes;
-- topup-fee-from-option ticket). Optional: a topup that matches no catalog entry is still logged
-- without one. The Option only suggests the amount; the Fee keeps its own, so a later price change
-- never touches it. Only a Topup Fee may reference an Option: FeeController refuses it first, and
-- the CHECK is the backstop.
ALTER TABLE fees ADD COLUMN topup_option_id UUID REFERENCES topup_options (id);

ALTER TABLE fees ADD CONSTRAINT chk_fees_topup_option_only_on_topup
    CHECK (topup_option_id IS NULL OR fee_type = 'TOPUP');

CREATE INDEX idx_fees_topup_option_id ON fees (topup_option_id);
