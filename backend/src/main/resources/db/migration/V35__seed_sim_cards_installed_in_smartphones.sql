-- Seed data holding both installed and uninstalled SIM Cards, including one Smartphone with two
-- (sim-installed-in-smartphone ticket AC), without adding any new row: the demo Contract's Pixel 8
-- (V17) gets both of its Contract's Postpaid SIMs (V17's own and V29's plan-priced one) installed
-- in it, reaching the two-SIM limit; the second Contract's SIM (V18) stays uninstalled.

UPDATE sim_cards SET installed_in_smartphone_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
WHERE id IN ('cccccccc-cccc-cccc-cccc-cccccccccccc', '56565656-5656-5656-5656-565656565656');
