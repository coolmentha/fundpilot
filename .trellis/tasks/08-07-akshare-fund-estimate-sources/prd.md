# 接入 AKShare 基金行情源

## Goal

研究并接入 AKShare 中与基金盘中估值、净值及相关行情有关的数据源，参考其真实调用方式，修复当前基金估值备用源不可用问题，并接入现有降级、限流、缓存和可观测性链路。

## Requirements

- 研究本机 AKShare 实现及其对应的真实外部接口，覆盖基金盘中估值、ETF/LOF 行情与净值相关源。
- 参考 AKShare 的请求 URL、查询参数、请求头、分页/批量方式和响应解析方式，接入现有 Java 行情数据源链。
- 修复当前基金估值备用源不可用的问题，并保留按北京时间判断数据新鲜度、失败显式暴露和不复用旧估值的现行契约。
- 不新增 Python/AKShare 运行时依赖；FundPilot 继续由 Java 服务直接调用外部源。
- 仅将语义上等价于基金盘中估值的数据接入估值链；交易价格、历史净值和已确认净值不得未经口径确认直接当作盘中估值。

## Acceptance Criteria

- [ ] 形成 AKShare 基金相关源清单，标明每个源的数据语义、覆盖基金类型、真实接口和可用性验证结果。
- [ ] 至少有一个可验证的基金估值备用源接入现有降级链，并覆盖超时、空响应、旧日期、解析失败和恢复成功测试。
- [ ] 估值请求量、批量刷新、限流和失败退避不会造成每只基金重复放大请求。
- [ ] 现有 `FundEstimateSnapshot`、缓存失效、三态涨跌和前端失败展示契约保持兼容。
- [ ] 相关后端测试、构建和真实源只读冒烟验证通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
