# 修复 QDII 当日收益确认日期

## Goal

修正行情工作台的 QDII 今日收益口径：收益只在净值首次被平台发现的北京时间当天结算，避免次日继续用最近确认净值重复显示前一日收益。

## Background

当前 [FundPnlService](../../backend/src/main/java/com/fundpilot/backend/fund/service/FundPnlService.java) 只要 QDII 存在两期净值，就无条件把最新两期当作当前收益依据，没有判断最新净值的 `firstSeenAt` 日期。

## Requirements

- QDII 最新净值的 `firstSeenAt` 按 `Asia/Shanghai` 转换为业务日期。
- 只有该业务日期等于今天时，才使用最新两期确认净值计算 QDII 今日涨跌和今日盈亏。
- 同一天存在多条新发现净值时，按 `navDate` 最大的一条作为最新确认净值；其上一条净值作为比较基线。
- `firstSeenAt` 不是今天时，QDII 今日涨跌和今日盈亏返回 `0`；最近确认净值仍可用于持仓市值和总盈亏。
- 普通基金现有的按 `navDate` 判断当日净值和估值三态逻辑保持不变。

## Acceptance Criteria

- [x] QDII 净值在今天首次发现时，按最新 `navDate` 与上一期净值计算今日收益。
- [x] QDII 净值在昨天或更早首次发现时，今天的今日涨跌和今日盈亏为 `0`，不重复计算最近确认净值的涨跌。
- [x] 同日多条 QDII 净值按最大 `navDate` 选择，批量和单基金计算结果一致。
- [x] QDII 最近确认净值仍用于持仓市值和总盈亏。
- [x] 现有后端测试、前端验证和 lint/build 不回归。

## Out Of Scope

- 不修改 `firstSeenAt` 的落库时机。
- 不修改净值历史表结构、估值缓存或普通基金收益规则。
