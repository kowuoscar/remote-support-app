-- manager-decides-return-disposition ticket: the Manager now chooses POSTED_TO_COMPANY for a
-- company-owned Smartphone and CANCELLED for a SIM Card at approval (spec.md Solution's
-- Disposition table), so returned_units.disposition needs to accept both values on top of the
-- POSTED_TO_CLIENT the return-client-owned-smartphones ticket's V46 migration already allowed --
-- same drop/re-add widen pattern as requests_type_check (V40/V44/V46). KEPT_IN_STOCK stays out
-- until the agent-stock ticket, which widens this CHECK again.
ALTER TABLE returned_units DROP CONSTRAINT returned_units_disposition_check;
ALTER TABLE returned_units ADD CONSTRAINT returned_units_disposition_check CHECK (
    disposition IN ('POSTED_TO_CLIENT', 'POSTED_TO_COMPANY', 'CANCELLED')
);
