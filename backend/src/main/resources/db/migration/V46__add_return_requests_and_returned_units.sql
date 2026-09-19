-- return-client-owned-smartphones ticket: Return is a brand-new Request type (spec.md Solution:
-- "Return type") -- a plain widen of the existing lifecycle CHECK, mirroring replace-requests'
-- V40 and manager-approves-requests' V44 (a net-new value, nothing to phase out).
ALTER TABLE requests DROP CONSTRAINT requests_type_check;
ALTER TABLE requests ADD CONSTRAINT requests_type_check CHECK (
    type IN (
        'REBOOT', 'TOPUP', 'SIM_SWAP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM',
        'REPLACE_SMARTPHONE', 'REPLACE_SIM', 'OTHER', 'RETURN'
    )
);

-- A Return holds SEVERAL units, each with its own Disposition (spec.md Solution's Disposition
-- table) -- unlike every other type's single target, so each named unit gets its own row rather
-- than more nullable columns on `requests` (carrier-catalog-notes.md: "model the returned units as
-- their own rows"). Exactly one of smartphone_id/sim_card_id is set per row: a Return names either
-- a Smartphone or a SIM Card, never both on the same row. `disposition` is nullable and, for now,
-- CHECK-constrained to only 'POSTED_TO_CLIENT' -- this ticket only ever writes that value, fixed at
-- submission, since a company-owned unit is refused outright; manager-decides-return-disposition
-- widens this CHECK (widen-then-narrow-free pattern, same as requests_type_check above) when it
-- leaves a company-owned unit's Disposition unset until the Manager chooses it at approval.
CREATE TABLE returned_units (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    request_id UUID NOT NULL REFERENCES requests (id),
    smartphone_id UUID REFERENCES smartphones (id),
    sim_card_id UUID REFERENCES sim_cards (id),
    disposition VARCHAR(24) CHECK (disposition IN ('POSTED_TO_CLIENT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_returned_units_exactly_one_unit CHECK (
        (smartphone_id IS NOT NULL AND sim_card_id IS NULL)
        OR (smartphone_id IS NULL AND sim_card_id IS NOT NULL)
    )
);

CREATE INDEX idx_returned_units_tenant_id ON returned_units (tenant_id);
CREATE INDEX idx_returned_units_request_id ON returned_units (request_id);
