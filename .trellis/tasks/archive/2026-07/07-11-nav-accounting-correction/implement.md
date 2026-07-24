# 实施计划

- [x] RED→GREEN：补单位净值与累计净值不同的交易确认测试，集中净值语义并修复两个确认入口。
- [x] RED→GREEN：补申购费进入成本测试，修复 lot 与基金成本价公式。
- [x] RED→GREEN：补历史 `tradeDate` 的 lot/赎回费/成熟期测试，修复日期来源。
- [x] RED→GREEN：补单位净值市值与总盈亏测试，修复事实账目投影。
- [x] RED→GREEN：实现历史账本重建，覆盖买入、卖出、调整和 onboarding 成本保留；转换复用两腿关联净额。
- [x] 增加 V17 一次性迁移状态、存量 DCA 去重和数据库唯一索引。
- [x] 增加启动重建 Runner，成功后只执行一次；异常由事务回滚并向启动过程传播。
- [x] 重置受旧口径影响的止盈运行周期。
- [x] 更新 `CONTEXT.md`、ADR/transaction consistency/fund fee spec 与过期注释。
- [x] 运行后端完整测试、空库与 V16→V17 升级验证、前端 lint/test/build、Trellis 校验。

## 风险点

- 历史 onboarding 成本不可从交易金额反推，必须在删除旧 lot 前保留快照。
- 转换两腿跨基金，重放顺序必须保证转出净额先于转入建 lot。
- 历史费率并非完整版本化数据，优先使用已落库 feeRate/redemption 明细；无历史证据时才按零费率保守降级并记录日志。
- 重建属于生产数据修复，执行前必须确认备份已存在。

## Validation

```powershell
cd backend
mvn test
cd ../frontend
npm run lint
npm test
npm run build
cd ..
git diff --check
python ./.trellis/scripts/task.py validate 07-11-nav-accounting-correction
```
