-- The free-text "assigned to" is removed everywhere (spec.md Solution -- Fleet model:
-- "Smartphone's assigned to is removed everywhere"; smartphone-owner-and-optional-serial ticket).
-- V17/V18 (applied, never edited) insert values into it -- this migration drops the column outright
-- rather than migrating its data anywhere, since nothing replaces it: Owner (V32) records who owns
-- the unit, not who it was handed to, and Fleet visibility already stays per Contract.
--
-- The serial also becomes optional here (spec.md: "Smartphone's serial becomes optional"): a
-- Smartphone can now be created without one and have it set later from the Fleet page.

ALTER TABLE smartphones DROP COLUMN assigned_to;

ALTER TABLE smartphones ALTER COLUMN serial DROP NOT NULL;
