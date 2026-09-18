-- reboot-and-topup-details ticket: a Reboot Request names the Smartphone to reboot, a Topup
-- Request names the SIM Card it tops up and, when the Carrier has one, the Topup Option bought.
-- Nullable: a Request that exists before this ticket has none of the three (spec.md "A Request
-- created before this feature has no details"), and only Reboot/Topup ever set one respectively.
ALTER TABLE requests
  ADD COLUMN target_smartphone_id UUID REFERENCES smartphones (id),
  ADD COLUMN target_sim_card_id UUID REFERENCES sim_cards (id),
  ADD COLUMN topup_option_id UUID REFERENCES topup_options (id);
