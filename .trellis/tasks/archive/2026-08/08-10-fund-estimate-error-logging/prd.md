# 完善基金估值失败日志

## Goal

让后端异常日志保留完整异常信息，避免只有重复的业务描述而无法定位真实解析、网络或运行时根因。

## Requirements

- 后端现有捕获异常后仅记录 `Throwable.getMessage()` 的日志，必须改为记录异常对象，使日志包含异常类型、完整堆栈和 cause 链。
- 保留现有基金代码、数据源、降级动作等业务上下文。
- 不改变日志级别、异常处理、数据源降级、缓存失效或返回状态。
- 不记录完整外部响应、Cookie、Token 或其他敏感信息。

## Acceptance Criteria

- [x] `FundEstimateService` 的同花顺、东方财富静态页、ETF IOPV 和 fundgz 失败日志可看到原始异常堆栈及根因。
- [x] 后端其他仅输出 `getMessage()` 的异常日志同样保留 Throwable。
- [x] 后端编译和相关基金估值测试通过。

## Notes

- 轻量机械修改，PRD-only；不新增日志封装或依赖。
