-- A Manager name (real-manager-dashboard): the name a Company Manager is known by, stored on
-- their Login because a Manager has no record of their own. Nullable; no data is written, so
-- every existing row (the V2 Manager included) stays unnamed.
ALTER TABLE users ADD COLUMN name varchar(100);
