# 技术设计

## 变更分组

1. 行为修复：业务异常、前端时区、净值业务日期。
2. 规范对齐：构造器注入、注释同步。
3. 质量门禁：ESLint、Vitest、项目 quality specs。

## 后端

- 为成本单价非法新增专用 `ErrorCode`，继续由 `GlobalExceptionHandler` 映射为 HTTP 400。
- `DailyNavConfirmService` 用 `ChinaTradingDate.toUtcDate(Instant.now())` 得到日期标签；外部 `jzrq` 字符串可在窄解析函数中临时解析为日期，再立即转为 Instant 标签比较。
- Spring 组件只移除手写构造器并增加 Lombok 注解，不调整依赖或任务行为。

## 前端

- 使用 `Intl.DateTimeFormat`，固定 `timeZone: 'Asia/Shanghai'` 和数字格式，避免依赖浏览器本地时区。
- ESLint 使用 flat config，覆盖 `src/**/*.{js,jsx}`，启用 React Hooks 与 React Refresh 推荐规则。
- Vitest 使用 jsdom，仅先覆盖不依赖页面装配的格式化工具，后续组件测试可沿用。

## 风险

- 日期字符串格式受 locale 影响：显式指定 `zh-CN` 并用 `formatToParts` 组装稳定格式。
- ESLint 首次接入可能暴露存量问题：仅修复真实告警，不做无关格式化。
- React 19/ESLint 插件兼容性：选择当前稳定版本并以实际 lint/build 验证。

## 回滚

行为修复、注释整理和前端工具链分文件独立，可分别回滚；无数据库迁移。

