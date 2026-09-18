-- A Smartphone gains an Owner, Client or company (spec.md Solution -- Fleet model;
-- smartphone-owner-and-optional-serial ticket). Every existing Smartphone becomes company-owned
-- (Orchestrator call: the business provisioned them) -- except the seeded demo Smartphone on the
-- Demo Client's second Contract (V18), set to Client so local seed data covers both Owners
-- without adding a new row, per the ticket's "seed additions stay minimal".

ALTER TABLE smartphones ADD COLUMN owner VARCHAR(16);

UPDATE smartphones SET owner = 'COMPANY' WHERE owner IS NULL;

UPDATE smartphones SET owner = 'CLIENT' WHERE id = '12121212-1212-1212-1212-121212121212';

ALTER TABLE smartphones ALTER COLUMN owner SET NOT NULL;

ALTER TABLE smartphones ADD CONSTRAINT chk_smartphones_owner CHECK (owner IN ('CLIENT', 'COMPANY'));
