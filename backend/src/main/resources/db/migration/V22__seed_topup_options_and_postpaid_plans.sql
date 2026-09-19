-- Seed Topup Options and Postpaid Plans for the seeded active Carriers (V20: AT&T, T-Mobile,
-- Verizon; all UNITED_STATES, so every price is in USD), so local testing of the Carriers page
-- starts with offers (carrier-catalog spec, Seed data). One Topup Option and one Postpaid Plan are
-- archived, so "show archived" has something to reveal. The ids are fixed so later seeds can
-- reference them (a Postpaid SIM on a Plan, a Topup Fee from an Option).
INSERT INTO topup_options (id, carrier_id, name, price, archived_at)
VALUES
    ('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'Prepaid Refill 25', 25.00, NULL),
    ('d0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'Prepaid Refill 50', 50.00, NULL),
    ('d0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000002', 'Data Pass 5GB', 15.00, NULL),
    ('d0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002', 'Data Pass 15GB', 30.00, NULL),
    ('d0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000002', 'Data Pass 1GB', 5.00, '2024-06-01T00:00:00Z'),
    ('d0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000003', 'Prepaid Refill 35', 35.00, NULL),
    ('d0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000003', 'International Add-on', 10.00, NULL);

INSERT INTO postpaid_plans (id, carrier_id, name, price, archived_at)
VALUES
    ('e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'Unlimited Starter', 65.99, NULL),
    ('e0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'Unlimited Premium', 85.99, NULL),
    ('e0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000002', 'Essentials', 60.00, NULL),
    ('e0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002', 'Go5G', 75.00, NULL),
    ('e0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000003', 'Unlimited Welcome', 65.00, NULL),
    ('e0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000003', 'Unlimited Plus', 80.00, NULL),
    ('e0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000003', 'Start Unlimited', 70.00, '2024-06-01T00:00:00Z');
