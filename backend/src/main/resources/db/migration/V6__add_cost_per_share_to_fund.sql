-- V5: fund 表新增 cost_per_share 列(持仓成本单价,ADR-0013)
ALTER TABLE fund ADD COLUMN cost_per_share NUMERIC(19, 8);
