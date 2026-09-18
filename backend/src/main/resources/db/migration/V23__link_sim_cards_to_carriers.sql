-- A SIM Card's carrier becomes a reference to a Carrier of the catalog (carrier-catalog spec,
-- SIM Card changes and Migration; sim-card-carrier ticket). The free-text column's data moves
-- first, then the column goes.
--
-- A SIM Card's Country is its Contract's Agent's Country. Free-text names are grouped per tenant
-- and Country, case-insensitively after trimming, keeping the first spelling found (oldest SIM
-- Card first). An active Carrier of that name already in the Country is reused rather than
-- duplicated -- the V20 seed's "Verizon" for V17's demo SIM Card, for one. A SIM Card with no
-- carrier, or a blank one, stays unlinked. No monthly fee is touched, so no Client Invoice total
-- moves.

ALTER TABLE sim_cards ADD COLUMN carrier_id UUID REFERENCES carriers (id);

CREATE INDEX idx_sim_cards_carrier_id ON sim_cards (carrier_id);

CREATE TEMPORARY TABLE sim_card_carrier_names ON COMMIT DROP AS
SELECT s.id AS sim_card_id,
       s.tenant_id,
       a.country,
       btrim(s.carrier) AS name,
       lower(btrim(s.carrier)) AS name_key,
       s.created_at
FROM sim_cards s
JOIN contracts c ON c.id = s.contract_id
JOIN agents a ON a.id = c.agent_id
WHERE s.carrier IS NOT NULL
  AND btrim(s.carrier) <> '';

DO $$
DECLARE
    carriers_created INTEGER;
    sim_cards_linked INTEGER;
BEGIN
    INSERT INTO carriers (id, tenant_id, country, name)
    SELECT gen_random_uuid(), first_spelling.tenant_id, first_spelling.country, first_spelling.name
    FROM (
        SELECT DISTINCT ON (tenant_id, country, name_key) tenant_id, country, name, name_key
        FROM sim_card_carrier_names
        ORDER BY tenant_id, country, name_key, created_at, sim_card_id
    ) first_spelling
    WHERE NOT EXISTS (
        SELECT 1
        FROM carriers existing
        WHERE existing.tenant_id = first_spelling.tenant_id
          AND existing.country = first_spelling.country
          AND lower(btrim(existing.name)) = first_spelling.name_key
          AND existing.archived_at IS NULL
    );
    GET DIAGNOSTICS carriers_created = ROW_COUNT;

    UPDATE sim_cards s
    SET carrier_id = k.id
    FROM sim_card_carrier_names n
    JOIN carriers k
      ON k.tenant_id = n.tenant_id
     AND k.country = n.country
     AND lower(btrim(k.name)) = n.name_key
     AND k.archived_at IS NULL
    WHERE s.id = n.sim_card_id;
    GET DIAGNOSTICS sim_cards_linked = ROW_COUNT;

    RAISE NOTICE 'V23 sim-card-carrier migration: created % Carrier(s), linked % SIM Card(s)',
        carriers_created, sim_cards_linked;
END $$;

ALTER TABLE sim_cards DROP COLUMN carrier;
