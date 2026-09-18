-- A SIM Card gains Installed in: the Smartphone it currently sits in, if any (spec.md Solution --
-- Fleet model; sim-installed-in-smartphone ticket). At most one Smartphone per SIM Card; the
-- two-SIM-per-Smartphone limit is a business rule, not a schema constraint -- enforced in the API
-- (SimInstallationService), the same way the spec's Constraints section calls for it, since a
-- CHECK/trigger can't see other rows cheaply and every write already goes through that one module.

ALTER TABLE sim_cards ADD COLUMN installed_in_smartphone_id UUID REFERENCES smartphones (id);

CREATE INDEX idx_sim_cards_installed_in_smartphone_id ON sim_cards (installed_in_smartphone_id);
