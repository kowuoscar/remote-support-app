-- Every Request gains an optional description, required only for the incoming Other type
-- (request-types-and-flow spec, Details at submission; other-replaces-repair ticket). Validation
-- of "required for Other" lives in the application (RequestController/FeeController), not here --
-- see V9's fee.description for the same nullable-column shape.
ALTER TABLE requests ADD COLUMN description TEXT;

-- Widen the type/fee_type CHECKs to also accept OTHER, while still accepting the outgoing REPAIR
-- (dropped in V31, once every existing REPAIR row has been rewritten). Splitting widen-then-narrow
-- across two migrations is what lets V31's UPDATE run without ever violating either CHECK: the
-- rows are never invalid, whichever migration last touched them.
ALTER TABLE requests DROP CONSTRAINT requests_type_check;
ALTER TABLE requests ADD CONSTRAINT requests_type_check CHECK (
    type IN ('REBOOT', 'TOPUP', 'SIM_SWAP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM', 'OTHER', 'REPAIR')
);

ALTER TABLE fees DROP CONSTRAINT fees_fee_type_check;
ALTER TABLE fees ADD CONSTRAINT fees_fee_type_check CHECK (
    fee_type IN ('TOPUP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM', 'OTHER', 'REPAIR')
);
