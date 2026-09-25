-- 提醒规则条件化:规则本体改存条件数组 JSON,支撑指标/关系/参数可配。
-- rule_type 与 threshold 保留为存量审计列(不再由应用写入),旧行统一翻译为等价条件:
--   RISE   -> 当日涨跌幅 高于  +threshold
--   DROP   -> 当日涨跌幅 低于  -threshold
--   PROFIT -> 持仓收益率 高于  +threshold
-- 与旧口径 observedValue >= threshold / <= -threshold 逐条等价。
ALTER TABLE alert_rule ADD COLUMN conditions TEXT;

UPDATE alert_rule SET conditions = format(
        '{"match":"ALL","conditions":[{"indicator":"%s","params":{},"relation":"%s","value":%s}]}',
        CASE rule_type
            WHEN 'PROFIT' THEN 'HOLDING_RETURN'
            ELSE 'DAILY_CHANGE'
        END,
        CASE rule_type
            WHEN 'DROP' THEN 'BELOW'
            ELSE 'ABOVE'
        END,
        CASE rule_type
            WHEN 'DROP' THEN -threshold
            ELSE threshold
        END)
WHERE conditions IS NULL;

ALTER TABLE alert_rule ALTER COLUMN conditions SET NOT NULL;
ALTER TABLE alert_rule ALTER COLUMN rule_type DROP NOT NULL;
ALTER TABLE alert_rule ALTER COLUMN threshold DROP NOT NULL;
ALTER TABLE alert_rule DROP CONSTRAINT ck_alert_rule_threshold;

-- 提醒记录同时落一份当次命中时的条件快照,规则被改动后仍可回溯当时的口径。
-- trigger_type 与 threshold 保留为存量审计列,新记录为空。
ALTER TABLE alert_notification ADD COLUMN conditions_snapshot TEXT;
ALTER TABLE alert_notification ALTER COLUMN trigger_type DROP NOT NULL;
ALTER TABLE alert_notification ALTER COLUMN threshold DROP NOT NULL;