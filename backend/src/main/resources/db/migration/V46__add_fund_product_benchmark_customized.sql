-- User-corrected benchmark (issue #146) must survive catalog sync (issue #157).
-- benchmark_customized guards the value from being overwritten by classifier results.
ALTER TABLE fund_product ADD COLUMN benchmark_customized BOOLEAN NOT NULL DEFAULT FALSE;
