# 修复逻辑止损部分卖出被强制全仓

## Goal

修复 GitHub Issue #119：逻辑止损采纳前端禁用部分卖出，后端校验请求与锁内事实持仓一致，补充回归测试并验证前后端质量门禁。

## Background

- `SELL + LOGIC_BROKEN` 的业务语义是一次性清空当前确认持仓。
- 当前前端采纳弹窗允许编辑卖出份额；当前后端读取锁内事实持仓并静默忽略 `actualShares`。
- 该组合会让用户选择的部分卖出被改写成全仓卖出，造成金融动作与界面不一致。

## Requirements

- 逻辑止损采纳界面明确显示全仓卖出，卖出份额固定为当前持仓且不可编辑。
- 后端在锁定基金并读取事实持仓后校验请求份额；请求份额与事实持仓不一致时抛出已有业务错误，不得静默改写请求。
- 合法全仓请求继续生成锁内事实持仓数量的 `PENDING DECREASE` 交易。
- `TRAILING_STOP` 保持现有可按请求份额部分卖出的行为。
- 更新现有旧行为测试，并补充不一致请求的回归测试。

## Acceptance Criteria

- [ ] `LOGIC_BROKEN` 采纳界面显示全仓语义并禁用份额编辑。
- [ ] `LOGIC_BROKEN` 的部分卖出请求返回明确 `ErrorCode`，不会生成交易。
- [ ] `LOGIC_BROKEN` 的合法全仓请求仍生成正确份额的 `PENDING DECREASE`。
- [ ] `TRAILING_STOP` 的现有部分卖出测试继续通过。
- [ ] 后端受影响测试通过，前端 `npm run lint`、`npm test`、`npm run build` 通过。

## Out of Scope

- 不改变信号生成策略、持仓计算、数据库结构或 `TRAILING_STOP` 语义。
- 不处理其他开放 Issue。
