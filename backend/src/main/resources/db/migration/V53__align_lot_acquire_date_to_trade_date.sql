-- 对齐批次取得日与交易发生日。V14 回填历史批次时 acquire_date 取 confirm_time(系统确认时间,
-- 如夜间确认任务运行时刻),而流水自 V16 起展示 trade_date(交易发生日),两套日期导致
-- 赎回费估算的取得日期、持有天数与流水对不上。
-- 经 acquire_tx_id 关联回原交易统一改用交易发生日;历史 fund_lot_redemption 的
-- 持有天数与手续费不重算(R6.2 不回溯),本修正仅影响后续的赎回费估算与确认。
-- 回填时的剩余份额划分按 confirm_time 顺序计算,两字段顺序不一致时各批次份额的
-- 划分保留原值(合计受真实卖出锚定不变),仅日期改为交易发生日。
UPDATE fund_lot l
SET acquire_date = ft.trade_date,
    updated_date = now()
FROM fund_transaction ft
WHERE l.acquire_tx_id = ft.id
  AND ft.trade_date IS NOT NULL
  AND l.acquire_date <> ft.trade_date;
