# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — FundPilot 后端"基金纪律策略"领域语言词典。
- **`docs/adr/`** — 已存在 ADR-0001（前高/持有期高点实时派生）、0002（东方财富真实数据源）、0003（反弹清空 0.5% 缓冲带）。读与你接触领域相关的那些。

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## File structure

Single-context repo（FundPilot 当前布局）：

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-derive-peak-navs-instead-of-storing.md
│   ├── 0002-real-eastmoney-data-source.md
│   └── 0003-tier-clear-buffer.md
├── backend/
│   └── docs/                            ← 设计文档（推荐架构方案 / 落地讨论 / 框架原文）
└── frontend/
```

> 备注：`backend/docs/` 下的设计文档是实现细节与状态机，不是领域上下文；`CONTEXT.md` 是领域语言词典，两者用途不同，读取时注意区分。

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids（`CONTEXT.md` 里以 `_Avoid_:` 标记的禁用词不可使用，例如：用 `BUILD/ADD/SELL` 而不是 `DECREASE`；不要把 `peakNav` 落字段；用"事实账目份额永远实时算"的硬性原则）。

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/grill-with-docs`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0001（前高不落字段实时派生）— but worth reopening because…_
