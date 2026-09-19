-- agent-stock ticket: the Manager may now choose Kept in Stock (returns-and-agent-stock spec,
-- Solution's Disposition table) for a company-owned Smartphone or a SIM Card at approval, on top
-- of the POSTED_TO_CLIENT/POSTED_TO_COMPANY/CANCELLED the earlier two tickets' V46/V48 migrations
-- already allowed -- a CHECK constraint can't be partially altered, so this re-runs the full widen
-- (manager-decides-return-disposition ticket notes: "agent-stock ... must re-run the full widen").
ALTER TABLE returned_units DROP CONSTRAINT returned_units_disposition_check;
ALTER TABLE returned_units ADD CONSTRAINT returned_units_disposition_check CHECK (
    disposition IN ('POSTED_TO_CLIENT', 'POSTED_TO_COMPANY', 'CANCELLED', 'KEPT_IN_STOCK')
);
