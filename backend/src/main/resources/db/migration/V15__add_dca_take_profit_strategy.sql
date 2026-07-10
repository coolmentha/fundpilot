-- 定投止盈参数与周期状态。保留 stop_loss_pullback_percent 列名以兼容既有 API/数据。
ALTER TABLE fund_strategy
    ADD COLUMN profit_activation_percent NUMERIC(19,8),
    ADD COLUMN profit_harvest_percent NUMERIC(19,8),
    ADD COLUMN minimum_holding_percent NUMERIC(19,8),
    ADD COLUMN max_single_sell_percent NUMERIC(19,8),
    ADD COLUMN cooldown_trading_days INTEGER,
    ADD COLUMN preset_fund_category VARCHAR(32),
    ADD COLUMN preset_version INTEGER,
    ADD COLUMN customized BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN take_profit_phase VARCHAR(32),
    ADD COLUMN cycle_started_at TIMESTAMPTZ,
    ADD COLUMN cycle_peak_nav NUMERIC(19,8),
    ADD COLUMN triggered_signal_id BIGINT,
    ADD COLUMN cooldown_started_at TIMESTAMPTZ;

UPDATE fund_strategy fs
SET stop_loss_pullback_percent = ABS(COALESCE(fs.stop_loss_pullback_percent,
    CASE COALESCE(f.fund_category, 'ACTIVE')
        WHEN 'BROAD_BASE' THEN 0.06
        WHEN 'SECTOR' THEN 0.08
        WHEN 'MIXED' THEN 0.05
        ELSE 0.07
    END)),
    profit_activation_percent = CASE COALESCE(f.fund_category, 'ACTIVE')
        WHEN 'BROAD_BASE' THEN 0.15
        WHEN 'SECTOR' THEN 0.20
        WHEN 'MIXED' THEN 0.12
        ELSE 0.15
    END,
    profit_harvest_percent = CASE COALESCE(f.fund_category, 'ACTIVE')
        WHEN 'MIXED' THEN 0.40
        ELSE 0.50
    END,
    minimum_holding_percent = CASE COALESCE(f.fund_category, 'ACTIVE')
        WHEN 'SECTOR' THEN 0.40
        WHEN 'MIXED' THEN 0.60
        ELSE 0.50
    END,
    max_single_sell_percent = 0.20,
    cooldown_trading_days = 10,
    preset_fund_category = COALESCE(f.fund_category, 'ACTIVE'),
    preset_version = 1,
    customized = TRUE,
    take_profit_phase = CASE WHEN fs.status = 'EFFECTIVE' THEN 'ACCUMULATING' ELSE NULL END
FROM fund f
WHERE f.id = fs.fund_id;

-- 配置完整性由 StrategyConfigService 校验。列保持可空以兼容历史数据、归档读取和
-- Repository 层最小实体测试；生产中的既有策略已在上方完成全量回填。
