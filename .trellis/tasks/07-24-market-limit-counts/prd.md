# 大盘涨停跌停家数

## Goal

在行情工作台的“大盘涨跌”区域补充沪深京市场的涨停、跌停家数。

## Requirements

- 保留现有上涨/下跌家数与比例条，并在同一区域展示涨停、跌停家数。
- 涨停、跌停与现有市场宽度使用相同的沪深京市场口径。
- 后端仅在后台行情刷新中请求外部数据；`GET /api/market/breadth` 继续只读 Redis/内存缓存。
- 上涨、下跌、涨停、跌停四项必须来自同一轮完整快照；任一项缺失、响应异常或日期不匹配时保留上次完整快照，不发布部分数据。
- 不新增依赖，不修改现有上涨/下跌统计口径。
- 同花顺 `indexflash` 每次刷新先访问主页建立 Cookie 会话，再请求统计接口；代码、配置示例和日志不得包含 Cookie 值。主页、接口或响应任一失败时保留上一完整快照。

## Acceptance Criteria

- [x] “大盘涨跌”展示上涨、下跌、涨停、跌停四项，并在无完整快照时保持现有不可用状态。
- [x] `/api/market/breadth` 返回四项整数；用户请求不触发外部行情调用。
- [x] 有效外部响应可解析出涨停、跌停总数；缺字段、空池、错误响应不覆盖旧快照。源响应不提供日期字段，交易时段调度是该数据的新鲜度边界。
- [x] Redis 恢复后的市场宽度保留四项数据。
- [x] 后端解析/缓存/控制器与前端展示均有自动化测试。
- [x] Cookie 会话由客户端动态建立，仓库内不存在实际 Cookie 值。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
