-- 基金费率缓存表:FundFeeService 从天天基金 jjfl_<code>.html 爬取并落库,
-- 供 TransactionConfirmSupport 买入扣申购费(discount_rate)、卖出按持有期查赎回费率阶梯。
-- 费率慢变(基金合同修改才改),每日 06:30 FundFeeRefreshJob 刷新 + ApplicationReadyEvent 预热。
-- fund_code 与 fund.fund_code 一致,唯一索引去重。
CREATE TABLE fund_fee (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    fund_code VARCHAR(255) NOT NULL,
    purchase_rate NUMERIC(19,8),
    discount_rate NUMERIC(19,8),
    sales_service_fee NUMERIC(19,8),
    redemption_ladder VARCHAR(2000),
    fetched_at TIMESTAMPTZ NOT NULL,
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_date TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_fund_fee_code ON fund_fee(fund_code) WHERE deleted_date IS NULL;

-- 买入 lot(税lot):每笔确认的买入(INCREASE/TRANSFER_IN/INVEST)建一行,
-- 卖出时按 acquire_date ASC FIFO 消耗 remaining_shares。供赎回费按持有期匹配。
-- fund_id / acquire_tx_id 为逻辑外键,不加 FK 约束(跟随项目既有约定,仅靠索引)。
CREATE TABLE fund_lot (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    fund_id BIGINT NOT NULL,
    acquire_tx_id BIGINT NOT NULL,
    acquire_date TIMESTAMPTZ NOT NULL,
    acquire_shares NUMERIC(19,8) NOT NULL,
    remaining_shares NUMERIC(19,8) NOT NULL,
    acquire_cost_per_share NUMERIC(19,8) NOT NULL,
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_date TIMESTAMPTZ
);
-- FIFO 查询索引:按 fund_id + acquire_date 升序遍历剩余份额 > 0 的 lot。
CREATE INDEX idx_fund_lot_fund_date ON fund_lot(fund_id, acquire_date) WHERE deleted_date IS NULL;

-- 卖出消耗 lot 记录:每笔卖出按 FIFO 拆成多行(每行对应一个被消耗的 lot),
-- 记录 holding_days(卖出确认日 - lot.acquireDate 自然日)和 redemption_rate,供校验与展示。
CREATE TABLE fund_lot_redemption (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    lot_id BIGINT NOT NULL,
    sell_tx_id BIGINT NOT NULL,
    shares_consumed NUMERIC(19,8) NOT NULL,
    holding_days INT NOT NULL,
    redemption_rate NUMERIC(19,8) NOT NULL,
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_date TIMESTAMPTZ
);
CREATE INDEX idx_fund_lot_redemption_lot ON fund_lot_redemption(lot_id) WHERE deleted_date IS NULL;
CREATE INDEX idx_fund_lot_redemption_sell ON fund_lot_redemption(sell_tx_id) WHERE deleted_date IS NULL;

-- fund_transaction 加手续费列:fee=本次交易手续费金额,fee_rate=加权费率(fee/金额,小数)。
-- 可空(历史数据 null + 费率缺失降级时 null,前端显示 '-')。
ALTER TABLE fund_transaction ADD COLUMN fee NUMERIC(19,8);
ALTER TABLE fund_transaction ADD COLUMN fee_rate NUMERIC(19,8);
