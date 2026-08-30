# 领域文档指引

本文件说明 Agent 和开发者应如何读取、引用和维护 FundPilot 的业务文档。

## 阅读顺序

开始分析业务前，按以下顺序读取：

1. 外部工作台提供的领域上下文：领域术语、产品边界和跨模块核心契约。
2. [`docs/business/`](../business/README.md)：当前业务流程、状态机、计算口径和失败语义。
3. [`docs/adr/`](../adr/)：与你当前任务相关的决策记录。
4. 当前代码和测试：验证文档描述是否仍与可执行行为一致。

产品视角可先读 [`docs/PRODUCT.md`](../PRODUCT.md)。

## 权威性

发生冲突时，使用以下顺序：

1. 当前代码与测试。
2. 未被后续决策取代的 ADR。
3. 外部工作台提供的领域上下文。
4. `docs/business/`。
5. `docs/PRODUCT.md`。
6. 旧 PRD、旧架构设计和原始策略框架。

不要因为旧文档更详细，就用旧方案覆盖当前实现。

## 当前领域结构

```text
/
├── docs/
│   ├── PRODUCT.md
│   ├── business/
│   │   ├── README.md
│   │   ├── fund-and-position.md
│   │   ├── transactions-and-accounting.md
│   │   ├── dca.md
│   │   ├── sell-discipline-and-signals.md
│   │   ├── market-and-pnl.md
│   │   └── capital-and-position-limit.md
│   └── adr/
└── backend/
    ├── 基金纪律策略框架.md
    └── docs/
```

`backend/基金纪律策略框架.md`、`backend/docs/` 和部分旧 PRD 是历史资料，正文中可能包含金字塔加仓、计划总仓位、回测寻优、BUILD/ADD 新信号或旧净值口径。它们只用于理解演进过程。

## 术语规则

- `SignalType` 表达策略建议，当前新信号只使用 `NONE/SELL`。
- `FundTransactionSource` 表达交易或账务修正来源；`INCREASE/DECREASE/TRANSFER_IN/TRANSFER_OUT/INVEST/ADJUST_IN/ADJUST_OUT` 改变份额，`COST_BASIS_RESET` 只重置当前成本基准。
- 不要把 `SELL` 信号与 `DECREASE` 交易来源当成同一个概念。
- `BUILD/ADD` 和 `CALIBRATED/CALIBRATION_FAILED` 是兼容遗留，不是当前主流程。
- 事实持仓只由 CONFIRMED 交易份额聚合，PENDING 不进入持仓。
- 单位净值用于记账和市值，累计净值用于复权分析和回撤。
- 北京时间决定业务日，日期标签存为 UTC 00:00 `Instant`。
- 常说的场外基金“7 天保护”在系统内按 5 个交易日计算；要明确是逐 lot 止盈保护、逻辑止损豁免，还是手动卖出绕过信号门控。

## 引用与更新

- Issue、PRD、测试名和设计说明应使用工作台领域上下文与 `docs/business/` 中的术语。
- 描述具体业务流程时，链接到唯一负责该主题的业务文档，避免在多个总览中复制完整规则。
- 新决策或取舍写入 ADR；新的跨模块不变量交由外部工作台维护；详细流程变化更新对应 `docs/business/*.md`。
- 行为变化时应同步检查代码注释、测试、业务文档和相关 ADR，不能只更新其中一处。
- 若代码行为与未被取代的 ADR 冲突，必须明确指出，不得静默重写历史。
