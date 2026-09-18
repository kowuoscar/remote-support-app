-- provision-request-details ticket: a Provision Smartphone Request names the model it wants; a
-- Provision SIM Request names the flavor, Carrier and, for postpaid, the Postpaid Plan it wants.
-- Nullable: a Request that exists before this ticket has none of the four (spec.md "A Request
-- created before this feature has no details"), and only Provision Smartphone/SIM ever set the
-- respective fields. Provision SIM's optional target Smartphone reuses the existing
-- target_smartphone_id column (reboot-and-topup-details ticket) rather than a second column --
-- Reboot and Provision SIM never both apply to the same Request, so the column is unambiguous.
ALTER TABLE requests
  ADD COLUMN requested_model VARCHAR(255),
  ADD COLUMN requested_flavor VARCHAR(20),
  ADD COLUMN requested_carrier_id UUID REFERENCES carriers (id),
  ADD COLUMN requested_postpaid_plan_id UUID REFERENCES postpaid_plans (id);
