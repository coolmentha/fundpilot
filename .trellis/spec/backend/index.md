# Backend Development Guidelines

> Best practices for backend development in this project.

---

## Overview

This directory contains guidelines for backend development. Fill in each file with your project's specific conventions.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Module organization and file layout | To fill |
| [Database Guidelines](./database-guidelines.md) | ORM patterns, queries, migrations | To fill |
| [Error Handling](./error-handling.md) | Error types, handling strategies | To fill |
| [Quality Guidelines](./quality-guidelines.md) | Code standards, forbidden patterns | Filled |
| [Logging Guidelines](./logging-guidelines.md) | Structured logging, log levels | To fill |
| [Market Realtime Cache](./market-realtime-cache.md) | 行情工作台实时数据缓存层契约(刷新频率、字段缩放、降级策略) | Filled |
| [Fund Fee Deduction](./fund-fee-deduction.md) | 交易手续费扣除(申购费+赎回费+FIFO lot 匹配、Jsoup HTML 爬虫、双确认路径共用 support) | Filled |
| [Transaction Consistency](./transaction-consistency.md) | 夜间确认、转换状态、ADJUST/lot 与日历并发幂等契约 | Filled |

---

## How to Fill These Guidelines

For each guideline file:

1. Document your project's **actual conventions** (not ideals)
2. Include **code examples** from your codebase
3. List **forbidden patterns** and why
4. Add **common mistakes** your team has made

The goal is to help AI assistants and new team members understand how YOUR project works.

---

**Language**: All documentation should be written in **English**.
