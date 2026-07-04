-- 指数 K 线缓存表:MarketDataFetchService 每日拉基准指数 K 线(算 VolumeState 用)时顺便落库,
-- 供 KlineService 读本地缓存渲染日/周/月 K,避免图表按需拉 push2his 触发 IP 限流(Unexpected end of file)。
-- index_code 用人类可读格式(如 930713.CSI / 000300.SH),与 fund.benchmark_index_code 一致,直接查。
CREATE TABLE index_kline (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    index_code VARCHAR(32) NOT NULL,
    trade_date TIMESTAMPTZ NOT NULL,
    open NUMERIC(19,8),
    high NUMERIC(19,8),
    low NUMERIC(19,8),
    close NUMERIC(19,8) NOT NULL,
    volume BIGINT,
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_date TIMESTAMPTZ
);

-- 同指数同日唯一(软删行除外)。每日同步按 index_code+trade_date 去重,只插缺失日期。
CREATE UNIQUE INDEX uq_index_kline_code_date ON index_kline(index_code, trade_date) WHERE deleted_date IS NULL;
CREATE INDEX idx_index_kline_code ON index_kline(index_code);
