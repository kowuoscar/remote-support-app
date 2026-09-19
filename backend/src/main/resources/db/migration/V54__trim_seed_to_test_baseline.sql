-- Trim the Flyway seed to the test baseline (demo-data-story spec.md Solution "Test baseline";
-- trim-seed-to-test-baseline ticket). Deletes every ad hoc demo row earlier seed migrations
-- inserted -- the Demo Client and its Contracts (V17, V18), the Agents other than Jordan Ellis
-- (V17, V18), the demo Tester login and its Fleet (V17, V18, V29), the Requests and Fee raised
-- against that Fleet (V26, V31, V45), and the one Carrier V23's own dynamic linking logic created
-- for it (a United Kingdom "EE", never given a fixed id of its own) -- plus, crucially, every row
-- a developer added *by hand* against any of those anchors on a database that was actually used
-- (spec.md Constraint: "the baseline migration ... succeeds on a local database that was used by
-- hand"). A developer can raise a Request through the UI, log a Fee, send a Client Invoice, put a
-- unit in an Agent's Stock, create a login for a legacy Agent, or add another Tester to the Demo
-- Client -- none of those rows have a fixed id of their own, so they can't be named directly. This
-- migration instead resolves each table's demo-connected rows dynamically, by following every
-- foreign key that can reach the fixed anchors (transitively), captures every id *before* any
-- delete runs (so later lookups aren't affected by earlier deletes), then deletes in dependency
-- order (children before parents). It never deletes by pattern or by whole table, and a row with
-- no path back to a fixed anchor -- Jordan Ellis, his Contracts with other Clients, other Clients
-- and Testers, the United States catalog -- is never touched.
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
    demo_client_id UUID := '77777777-7777-7777-7777-777777777777';
    demo_agent_ids UUID[] := ARRAY[
        'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        'ffffffff-ffff-ffff-ffff-ffffffffffff'
    ]::UUID[];
    demo_contract_ids UUID[];
    demo_tester_ids UUID[];
    demo_request_ids UUID[];
    demo_fee_ids UUID[];
    demo_client_invoice_ids UUID[];
    demo_smartphone_ids UUID[];
    demo_sim_card_ids UUID[];
    demo_user_ids UUID[];
    demo_uk_carrier_id UUID;
    removed INTEGER;
BEGIN
    -- Every foreign key that can reach the fixed anchors, resolved bottom-up (parents' id sets
    -- first, since children are looked up by them) but captured entirely before any DELETE, so
    -- every lookup below sees the database exactly as a developer left it.

    -- Contracts (V17 'aaaaaaaa...', V18 'dddddddd...') plus any Contract a developer created by
    -- hand on the Demo Client (any Agent) or with a demo Agent (any Client).
    SELECT array_agg(id) INTO demo_contract_ids
    FROM contracts
    WHERE client_id = demo_client_id OR agent_id = ANY (demo_agent_ids);

    -- Testers (V17 '88888888...') plus any Tester a developer added to the Demo Client by hand.
    SELECT array_agg(id) INTO demo_tester_ids
    FROM testers
    WHERE client_id = demo_client_id;

    -- Requests (V26, V31, V45) plus any Request a developer raised through the UI on a demo
    -- Contract or on behalf of the demo Tester -- the observed bug: three such Requests (a Reboot
    -- and two Provision SIMs) had no fixed id, so the old fixed-id-only DELETE never reached them,
    -- and deleting the Contract/Tester/its login underneath them then violated their FKs.
    SELECT array_agg(id) INTO demo_request_ids
    FROM requests
    WHERE contract_id = ANY (coalesce(demo_contract_ids, ARRAY[]::UUID[]))
       OR tester_id = ANY (coalesce(demo_tester_ids, ARRAY[]::UUID[]));

    -- Fees (V26) plus any Fee a developer logged by hand against a demo Contract or Request.
    SELECT array_agg(id) INTO demo_fee_ids
    FROM fees
    WHERE contract_id = ANY (coalesce(demo_contract_ids, ARRAY[]::UUID[]))
       OR request_id = ANY (coalesce(demo_request_ids, ARRAY[]::UUID[]));

    -- Client Invoices a developer sent/drafted by hand against a demo Contract (never seeded by a
    -- fixed id).
    SELECT array_agg(id) INTO demo_client_invoice_ids
    FROM client_invoices
    WHERE contract_id = ANY (coalesce(demo_contract_ids, ARRAY[]::UUID[]));

    -- Smartphones (V17, V18) plus any a developer added to a demo Contract's Fleet, or moved into
    -- a demo Agent's Stock (agent-stock: contract_id is then NULL, holding_agent_id is set).
    SELECT array_agg(id) INTO demo_smartphone_ids
    FROM smartphones
    WHERE contract_id = ANY (coalesce(demo_contract_ids, ARRAY[]::UUID[]))
       OR holding_agent_id = ANY (demo_agent_ids);

    -- SIM Cards (V17, V18, V29), same reasoning.
    SELECT array_agg(id) INTO demo_sim_card_ids
    FROM sim_cards
    WHERE contract_id = ANY (coalesce(demo_contract_ids, ARRAY[]::UUID[]))
       OR holding_agent_id = ANY (demo_agent_ids);

    -- Logins: the demo Tester's own login (V17 '99999999...') and any hand-made Tester login
    -- added to the Demo Client, plus any login a developer created for a demo Agent (the
    -- "Create login" flow -- both demo Agents start with none, but nothing stops adding one).
    SELECT array_agg(id) INTO demo_user_ids
    FROM users
    WHERE id IN (SELECT user_id FROM testers WHERE id = ANY (coalesce(demo_tester_ids, ARRAY[]::UUID[])))
       OR agent_id = ANY (demo_agent_ids);

    -- V23's own dynamic "link a SIM Card's free-text carrier to a Carrier" logic created a fresh
    -- United Kingdom Carrier ("EE") on the fly for V18's SIM Card -- captured here, one hop off
    -- that fixed SIM Card id, before the SIM Card itself is deleted below.
    SELECT carrier_id INTO demo_uk_carrier_id
    FROM sim_cards
    WHERE id = '34343434-3434-3434-3434-343434343434';

    -- Deletes, children before parents, every one scoped to the id sets captured above.

    DELETE FROM client_invoice_fee_snapshots
    WHERE client_invoice_id = ANY (coalesce(demo_client_invoice_ids, ARRAY[]::UUID[]))
       OR fee_id = ANY (coalesce(demo_fee_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from client_invoice_fee_snapshots', removed;

    DELETE FROM carrier_invoice_files
    WHERE client_invoice_id = ANY (coalesce(demo_client_invoice_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from carrier_invoice_files', removed;

    DELETE FROM returned_units WHERE request_id = ANY (coalesce(demo_request_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from returned_units', removed;

    DELETE FROM fees WHERE id = ANY (coalesce(demo_fee_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from fees', removed;

    DELETE FROM requests WHERE id = ANY (coalesce(demo_request_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from requests', removed;

    DELETE FROM sim_cards WHERE id = ANY (coalesce(demo_sim_card_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from sim_cards', removed;

    DELETE FROM smartphones WHERE id = ANY (coalesce(demo_smartphone_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from smartphones', removed;

    DELETE FROM client_invoices WHERE id = ANY (coalesce(demo_client_invoice_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from client_invoices', removed;

    DELETE FROM contracts WHERE id = ANY (coalesce(demo_contract_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from contracts', removed;

    DELETE FROM testers WHERE id = ANY (coalesce(demo_tester_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from testers', removed;

    DELETE FROM agent_standing_amounts WHERE agent_id = ANY (demo_agent_ids);
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from agent_standing_amounts', removed;

    DELETE FROM agent_invoices WHERE agent_id = ANY (demo_agent_ids);
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from agent_invoices', removed;

    DELETE FROM clients WHERE id = demo_client_id;
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from clients', removed;

    DELETE FROM users WHERE id = ANY (coalesce(demo_user_ids, ARRAY[]::UUID[]));
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from users', removed;

    DELETE FROM agents WHERE id = ANY (demo_agent_ids);
    GET DIAGNOSTICS removed = ROW_COUNT;
    RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from agents', removed;

    -- The dynamically-created United Kingdom Carrier (V23), now unreferenced. Guarded against
    -- ever being one of the fixed United States catalog ids (V20), so this can never remove
    -- catalog data even if a future migration changes how it's captured above, and against still
    -- being referenced by anything else (a Topup Option/Postpaid Plan/SIM Card/Request a
    -- developer added to it by hand after it appeared in their Carrier catalog) -- left in place,
    -- unremoved, rather than risk breaking an unrelated row it's no longer safe to call "only
    -- demo".
    IF demo_uk_carrier_id IS NOT NULL
        AND demo_uk_carrier_id NOT IN (
            'c0000000-0000-0000-0000-000000000001',
            'c0000000-0000-0000-0000-000000000002',
            'c0000000-0000-0000-0000-000000000003',
            'c0000000-0000-0000-0000-000000000004'
        )
        AND NOT EXISTS (SELECT 1 FROM sim_cards WHERE carrier_id = demo_uk_carrier_id)
        AND NOT EXISTS (SELECT 1 FROM requests WHERE requested_carrier_id = demo_uk_carrier_id)
        AND NOT EXISTS (SELECT 1 FROM topup_options WHERE carrier_id = demo_uk_carrier_id)
        AND NOT EXISTS (SELECT 1 FROM postpaid_plans WHERE carrier_id = demo_uk_carrier_id)
    THEN
        DELETE FROM carriers WHERE id = demo_uk_carrier_id;
        GET DIAGNOSTICS removed = ROW_COUNT;
        RAISE NOTICE 'V54 trim-seed-to-test-baseline: removed % row(s) from carriers', removed;
    END IF;
END $$;
