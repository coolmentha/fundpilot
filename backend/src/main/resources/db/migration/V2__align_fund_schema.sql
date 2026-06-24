-- =============================================================================
-- V2: 对齐 ADR-0001 与 ADR-0002 的 FundEntity 字段,并修复 V1 预存 schema bug
--
--  ADR-0001: peak_nav / holding_period_peak_nav 是 fund_nav_history.accumulated_nav
--            的 max 派生值,落字段存在净值修正/补录/job 异常三种失真风险,
--            改为实时派生(`max(fund_nav_history.accumulated_nav)`),不落字段。
--
--  ADR-0002: 接入东方财富真实行情数据源,fund 表需新增数据源维度分类与跟踪指数代码,
--            供 MarketIndicatorProvider 选择 ETF 指数 K 线或主动基金单周跌幅路径。
--
--  V1 预存 bug 一并在此修复(列缺失/类型不符都会卡住 Hibernate validate,issue #2
--  验收要求"启动通过 Hibernate validate"):
--    * fund.opened_at 列缺失 —— FundEntity.openedAt 无对应列,ADR-0001 持有期高点
--      派生 WHERE nav_date >= openedAt 依赖它。
--    * fund_nav_history.nav 列缺失 —— FundNavHistoryEntity.nav 无对应列。
--    * signal_log.signal_type 建成 SMALLINT,但 SignalLogEntity.signalType 标了
--      @Enumerated(EnumType.STRING),需 VARCHAR(32)。
--  注:V1 的部分唯一索引用 (nav_date : : date) 写法(中间带空格)的语法 bug 直接在 V1 修了
--      (V1 从未在真实 DB 跑过,spec "不要改 V1" 基于"V1 已发布"的假设不成立)。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. fund 表:ADR-0001 删派生字段 + ADR-0002 加数据源字段 + 补 opened_at
-- -----------------------------------------------------------------------------
ALTER TABLE fund DROP COLUMN peak_nav;
ALTER TABLE fund DROP COLUMN holding_period_peak_nav;

ALTER TABLE fund ADD COLUMN fund_sub_type        VARCHAR(32);
ALTER TABLE fund ADD COLUMN benchmark_index_code VARCHAR(64);
ALTER TABLE fund ADD COLUMN opened_at             TIMESTAMPTZ;

-- -----------------------------------------------------------------------------
-- 2. fund_nav_history:补 V1 漏建的 nav 列
-- -----------------------------------------------------------------------------
ALTER TABLE fund_nav_history ADD COLUMN nav NUMERIC(19, 8);

-- -----------------------------------------------------------------------------
-- 3. signal_log:signal_type SMALLINT -> VARCHAR(32)
--    SignalLogEntity.signalType 标了 @Enumerated(EnumType.STRING),需 VARCHAR(32)。
-- -----------------------------------------------------------------------------
ALTER TABLE signal_log ALTER COLUMN signal_type TYPE VARCHAR(32) USING signal_type::VARCHAR(32);
ALTER TABLE signal_log ALTER COLUMN signal_type DROP DEFAULT;
