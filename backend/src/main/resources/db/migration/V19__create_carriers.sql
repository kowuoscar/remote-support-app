-- The Carrier catalog (carrier-catalog spec; agent-maintains-carriers ticket): a mobile operator
-- in one Country. Currency is never stored -- it is always the Country's. A Carrier is archived,
-- never deleted, so SIM Cards and Fees referencing it later always stay readable.
CREATE TABLE carriers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    country VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_carriers_tenant_country ON carriers (tenant_id, country);

-- A name is unique among the active Carriers of one Country, ignoring case: an archived
-- Carrier's name can be reused. Enforced here as defence in depth and in CarrierController,
-- which returns a clean 409 first -- the same pattern as V4's one-primary-contact-per-Client
-- index.
CREATE UNIQUE INDEX uq_carriers_active_name_per_country
    ON carriers (tenant_id, country, lower(name))
    WHERE archived_at IS NULL;
