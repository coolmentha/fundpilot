# 业务逻辑文档整理实施计划

## 实施步骤

- [x] 按用户最新要求回滚 `CONTEXT.md`，保留其原有内容，不纳入本次正式文档修改。
- [x] 新建 `docs/business/README.md` 和六份主题文档，按设计模板写入当前业务流程、状态机、失败语义及实现证据。
- [x] 更新 `docs/PRODUCT.md`，增加详细业务文档入口，只修正经代码确认的当前能力描述。
- [x] 更新 `docs/agents/domain.md` 与 `AGENTS.md` 的领域文档入口和权威性说明，移除过时术语指导。
- [x] 给四份历史文档添加历史状态提示，不移动、不删除、不重写正文。
- [x] 对照关键 Service、Job、枚举和测试复查所有状态、时间、净值和交易口径。
- [x] 执行链接检查、关键术语扫描、空白错误检查和最终 diff 审阅。

## 重点实现证据

- 基金与持仓：`FundService`、`FundPositionService`、`FundServiceTest`、`FundPositionServiceTest`
- 交易与记账：`FundTransactionService`、`TransactionConfirmService`、`NavConfirmService`、`TransactionConfirmSupport` 及对应测试
- 定投：`DcaPlanService`、`DcaSuggestionService`、`DcaSuggestionJob*Test`
- 策略与信号：`StrategyConfigService`、`TakeProfitLifecycleService`、`DisciplineStrategyService`、`SignalGenerationService`、`SignalOperationService` 及对应测试
- 行情与盈亏：`MarketRealtimeCache`、`MarketDataFetchService`、`DailyNavConfirmService`、`FundPnlService` 及对应测试
- 资金池与仓位：`UserConfigService`、`PositionLimitService`、ADR-0020 及对应测试

## 验证命令

```powershell
git diff --check
```

```powershell
rg -n "plannedTotalAmount|totalInvestableCapital|金字塔加仓|BUILD/ADD 新信号|累计净值.*(市值|成本|成交)" docs/business docs/PRODUCT.md docs/agents/domain.md AGENTS.md
```

```powershell
$files = @('docs/PRODUCT.md', 'docs/agents/domain.md', 'AGENTS.md') + (Get-ChildItem docs/business -Filter *.md | ForEach-Object FullName)
$broken = foreach ($file in $files) {
    $absoluteFile = (Resolve-Path $file).Path
    $base = Split-Path $absoluteFile
    $content = Get-Content -Raw $absoluteFile
    foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\((?!https?://|#)([^)#]+)(?:#[^)]+)?\)')) {
        $target = [uri]::UnescapeDataString($match.Groups[1].Value)
        $resolved = Join-Path $base $target
        if (-not (Test-Path $resolved)) { "$absoluteFile -> $target" }
    }
}
if ($broken) { $broken; exit 1 }
```

文档任务不运行完整后端和前端测试；若整理过程中发现必须依赖运行结果才能确认的规则，再补充最小相关测试命令。

## 审阅重点

- 当前能力、兼容遗留、历史方案是否分层清楚。
- `FundStatus`、交易状态、策略状态、止盈周期和定投计划状态是否互不混淆。
- 单位净值与累计净值用途是否全局一致。
- 信号建议、自动定投和手动交易的授权边界是否清楚。
- 时间描述是否统一为北京时间业务日和 UTC 00:00 日期标签。
- 未触碰 `.research_tmp/` 和其他既有未跟踪 Trellis 任务。

## 验证结果

- Trellis 任务校验：通过。
- 变更文档内部链接检查：通过。
- Markdown 代码围栏配对检查：通过。
- `TODO`、`TBD`、机器绝对路径和本地 URL 扫描：无命中。
- 关键遗留术语扫描：在本次新增和更新的当前文档中，仅在“不再支持”或“历史兼容”说明中命中；`CONTEXT.md` 按用户要求保留原状。
- `git diff --check`：通过；仅输出仓库现有 LF/CRLF 转换提示，无空白错误。
- 未运行后端/前端测试：本次没有代码、配置、依赖或运行行为变更。

## Spec 同步结论

本任务没有新增或修改 API、数据库、跨层载荷、错误码或运行时契约，无需更新 `.trellis/spec/`。业务知识已经进入 `CONTEXT.md`、`docs/business/` 和 `docs/agents/domain.md`，不在 code-spec 中重复维护第二份事实源。
