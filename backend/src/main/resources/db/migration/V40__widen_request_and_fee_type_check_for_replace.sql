-- replace-requests ticket: Replace Smartphone and Replace SIM are brand-new Request/Fee types,
-- with no existing row ever having used either value -- unlike other-replaces-repair's REPAIR ->
-- OTHER rename (V30/V31), there is nothing to migrate and nothing to narrow back down afterwards,
-- so this is a plain widen with no matching "narrow" migration (see carrier-catalog-notes.md: the
-- widen-then-narrow pattern is for phasing an old value out, not for adding a value that was never
-- there to begin with).
ALTER TABLE requests DROP CONSTRAINT requests_type_check;
ALTER TABLE requests ADD CONSTRAINT requests_type_check CHECK (
    type IN (
        'REBOOT', 'TOPUP', 'SIM_SWAP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM',
        'REPLACE_SMARTPHONE', 'REPLACE_SIM', 'OTHER'
    )
);

ALTER TABLE fees DROP CONSTRAINT fees_fee_type_check;
ALTER TABLE fees ADD CONSTRAINT fees_fee_type_check CHECK (
    fee_type IN (
        'TOPUP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM', 'REPLACE_SMARTPHONE', 'REPLACE_SIM', 'OTHER'
    )
);
