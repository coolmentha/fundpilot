# 实施计划

- [x] 为历史 `PENDING` 补偿建立失败测试：03:00 时缺净值，稍后补齐后可确认。
- [x] 扩展确认服务，支持按基金补偿并隔离单笔失败。
- [x] 修改手动确认路径，按交易发生日查询累计净值并覆盖缺失净值错误用例。
- [x] 在两条净值落库路径完成后触发对应基金确认。
- [x] 新增低频补偿 Job，并为所有相关 cron 指定 `Asia/Shanghai`。
- [x] 补充 Job 时区、重启后补偿、错误隔离和手续费回填测试。
- [x] 更新交易一致性 spec，记录“净值落库必须推进待确认交易”的契约。
- [x] 运行后端定向测试、完整测试、`git diff --check` 和 Trellis 校验。

## 重点文件

- `backend/src/main/java/com/fundpilot/backend/fund/service/NavConfirmService.java`
- `backend/src/main/java/com/fundpilot/backend/fund/job/NavConfirmJob.java`
- `backend/src/main/java/com/fundpilot/backend/market/service/DailyNavConfirmService.java`
- `backend/src/main/java/com/fundpilot/backend/market/service/MarketDataFetchService.java`
- `backend/src/test/java/com/fundpilot/backend/fund/**`
- `backend/src/test/java/com/fundpilot/backend/market/**`

## 验证命令

```powershell
cd backend
mvn test
cd ..
git diff --check
python ./.trellis/scripts/task.py validate 07-10-pending-transaction-compensation
```
