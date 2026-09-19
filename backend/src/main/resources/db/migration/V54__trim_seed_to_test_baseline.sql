-- Trim the Flyway seed to the test baseline (demo-data-story spec.md Solution "Test baseline";
-- trim-seed-to-test-baseline ticket). Deletes every ad hoc demo row earlier seed migrations
-- inserted -- the Demo Client and its Contracts (V17, V18), the Agents other than Jordan Ellis
-- (V17, V18), the demo Tester login and its Fleet (V17, V18, V29), the Requests and Fee raised
-- against that Fleet (V26, V31, V45), and the one Carrier V23's own dynamic linking logic created
-- for it (a United Kingdom "EE", never given a fixed id of its own) -- by fixed id only, in
-- dependency order (children before parents), never by pattern or by table, so any hand-made row
-- a developer added by hand is untouched.
--
-- What remains after this migration is exactly the test baseline: the seeded tenant, the Manager
-- login, the Agent login and its Agent (Jordan Ellis, United States) with its standing-salary
-- backfill row, the unlinked Tester login, and the United States Carrier catalog with its Topup
-- Options and Postpaid Plans (V2/V3/V5/V14/V20/V22) -- exactly what
-- IntegrationTest/ContractApiTest/TopupFeeFromOptionApiTest and the migration tests targeting an
-- earlier version still reference. No applied migration is edited; a later `demo` profile loader
-- (demo-story-loader ticket) rebuilds a full story on its own database instead.
DO $$
DECLARE
    demo_uk_carrier_id UUID;
    removed INTEGER;
BEGIN
    -- V23 created a fresh United Kingdom Carrier ("EE") on the fly for the demo Client's second
    -- Contract's SIM Card (V18) -- its id was never fixed by any migration, so it's captured here,
    -- one hop via that SIM Card's own fixed id, before the SIM Card is deleted below.
    SELECT carrier_id INTO demo_uk_carrier_id
    FROM sim_cards
    WHERE id = '34343434-3434-3434-3434-343434343434';

    -- Fees (V26): the demo Topup Fee bought from a Topup Option.
    DELETE FROM fees WHERE id = 'f2000000-0000-0000-0000-000000000001';
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from fees', removed;

    -- Requests (V26, V31, V45): the demo Topup, the demo Other repair, the Pending Approval
    -- Provision Smartphone, and the Rejected Replace SIM.
    DELETE FROM requests WHERE id IN (
        'f1000000-0000-0000-0000-000000000001',
        'f3000000-0000-0000-0000-000000000001',
        'f4400000-0000-0000-0000-000000000001',
        'f4400000-0000-0000-0000-000000000002'
    );
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from requests', removed;

    -- SIM Cards (V17, V18, V29): the demo Contract's two Postpaid SIMs and the second Contract's
    -- SIM.
    DELETE FROM sim_cards WHERE id IN (
        'cccccccc-cccc-cccc-cccc-cccccccccccc',
        '56565656-5656-5656-5656-565656565656',
        '34343434-3434-3434-3434-343434343434'
    );
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from sim_cards', removed;

    -- Smartphones (V17, V18): the demo Contract's Pixel 8 and the second Contract's iPhone 15.
    DELETE FROM smartphones WHERE id IN (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        '12121212-1212-1212-1212-121212121212'
    );
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from smartphones', removed;

    -- Contracts (V17, V18): the Demo Client's two Contracts.
    DELETE FROM contracts WHERE id IN (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'dddddddd-dddd-dddd-dddd-dddddddddddd'
    );
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from contracts', removed;

    -- Testers (V17): the demo Tester profile linking the demo login to the Demo Client.
    DELETE FROM testers WHERE id = '88888888-8888-8888-8888-888888888888';
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from testers', removed;

    -- Agents other than Jordan Ellis (V17: Demo Agent; V18: Priya Shah).
    DELETE FROM agents WHERE id IN (
        'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        'ffffffff-ffff-ffff-ffff-ffffffffffff'
    );
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from agents', removed;

    -- The Demo Client (V17).
    DELETE FROM clients WHERE id = '77777777-7777-7777-7777-777777777777';
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from clients', removed;

    -- The demo Tester login, demo.tester@example.com (V17).
    DELETE FROM users WHERE id = '99999999-9999-9999-9999-999999999999';
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from users', removed;

    -- The dynamically-created United Kingdom Carrier (V23), now unreferenced. Guarded against ever
    -- being one of the fixed United States catalog ids (V20), so this can never remove catalog
    -- data even if a future migration changes how it's captured above.
    IF demo_uk_carrier_id IS NOT NULL
        AND demo_uk_carrier_id NOT IN (
            'c0000000-0000-0000-0000-000000000001',
            'c0000000-0000-0000-0000-000000000002',
            'c0000000-0000-0000-0000-000000000003',
            'c0000000-0000-0000-0000-000000000004'
        )
    THEN
        DELETE FROM carriers WHERE id = demo_uk_carrier_id;
        GET DIAGNOSTICS removed = ROW_COUNT;
        RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from carriers', removed;
    END IF;
END $$;
