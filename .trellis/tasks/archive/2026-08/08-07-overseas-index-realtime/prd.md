# 新增国外实时指数

## Goal

在设置页增加国外指数候选项，让用户通过现有关注指数配置和行情工作台实时指数条查看纳斯达克、日经225、韩国KOSPI。

## Requirements

- 在现有指数候选列表中增加以下东方财富 secid：
  - `100.NDX`：纳斯达克
  - `100.N225`：日经225
  - `100.KS11`：韩国KOSPI
- 复用现有关注指数保存接口、后端缓存和实时指数展示链路。
- 保留现有 A 股指数候选项及默认配置行为。
- 本任务不增加指数 K 线能力，不修改后端、数据库或外部数据源协议。

## Acceptance Criteria

- [ ] 设置页可选择并保存三个国外指数代码。
- [ ] 现有实时指数条能按后端返回结果展示国外指数名称、点位和涨跌幅。
- [ ] 现有 A 股指数选择、保存和展示行为不变。
- [ ] 前端 lint、测试和构建通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
