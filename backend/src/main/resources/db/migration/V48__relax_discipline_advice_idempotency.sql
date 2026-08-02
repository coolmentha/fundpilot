-- 建议接受→撤单→再接受允许重新建账：
-- 唯一索引从"每建议仅一笔未删除账目"收紧为"每建议仅一笔未取消账目"，
-- CANCELLED 旧账目不再占用幂等键，重新接受可创建新的 PENDING。
DROP INDEX IF EXISTS uq_fund_transaction_discipline_advice;
CREATE UNIQUE INDEX uq_fund_transaction_discipline_advice
    ON fund_transaction (discipline_advice_id)
    WHERE discipline_advice_id IS NOT NULL AND deleted_date IS NULL AND status <> 'CANCELLED';
