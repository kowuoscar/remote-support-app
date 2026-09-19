-- A Carrier's offers (carrier-catalog spec; topup-options-and-postpaid-plans ticket): Topup Options
-- and Postpaid Plans, each a name and a positive price in the Carrier's Country's currency. The
-- currency is never stored. Like a Carrier, an entry is archived, never deleted, so the SIM Cards
-- and Fees that will reference it always stay readable. Tenant scoping comes through the Carrier.
CREATE TABLE topup_options (
    id UUID PRIMARY KEY,
    carrier_id UUID NOT NULL REFERENCES carriers (id),
    name VARCHAR(255) NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_topup_options_carrier ON topup_options (carrier_id);

CREATE TABLE postpaid_plans (
    id UUID PRIMARY KEY,
    carrier_id UUID NOT NULL REFERENCES carriers (id),
    name VARCHAR(255) NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_postpaid_plans_carrier ON postpaid_plans (carrier_id);

-- A name is unique among the active Options, or the active Plans, of one Carrier, ignoring case:
-- an archived entry's name can be reused. Defence in depth behind CarrierOfferController's own
-- 409, as V19 does for Carrier names.
CREATE UNIQUE INDEX uq_topup_options_active_name_per_carrier
    ON topup_options (carrier_id, lower(name))
    WHERE archived_at IS NULL;

CREATE UNIQUE INDEX uq_postpaid_plans_active_name_per_carrier
    ON postpaid_plans (carrier_id, lower(name))
    WHERE archived_at IS NULL;
