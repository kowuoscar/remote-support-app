-- sim-swap-moves ticket: a SIM Swap Request's first move reuses the existing target_sim_card_id
-- (the SIM Card to move) and target_smartphone_id (its destination) columns
-- (reboot-and-topup-details/provision-request-details tickets) — no other type ever sets both of
-- those on the same Request, so they're unambiguous here too. A second move, only ever set for an
-- exchange of two SIM Cards, needs its own two columns. Nullable: a plain single-SIM move sets
-- neither, and a SIM Swap Request that exists before this ticket sets none of the four.
ALTER TABLE requests
  ADD COLUMN second_sim_card_id UUID REFERENCES sim_cards (id),
  ADD COLUMN second_target_smartphone_id UUID REFERENCES smartphones (id);
