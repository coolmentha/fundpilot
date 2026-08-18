# 详情页行情指标移至第一列

## Goal

用户进入基金详情页时，优先看到行情指标，减少查看当日分时和历史走势的操作步骤。

## Background

- 基金详情页使用 Ant Design `Tabs` 展示交易流水、策略参数、纪律建议、行情指标和定投计划。
- 当前“交易流水”位于第一项并作为默认激活项，“行情指标”位于第四项。

## Requirements

- 将“行情指标”移动到基金详情页 Tab 列表第一项。
- 将“行情指标”设为基金详情页默认激活项。
- 保持行情指标内容、其他 Tab 内容及其相对顺序不变。

## Acceptance Criteria

- [x] 基金详情页的第一个 Tab 标签为“行情指标”。
- [x] 首次进入基金详情页时展示“行情指标”内容。
- [x] 其余 Tab 仍按“交易流水、策略参数、纪律建议、定投计划”的顺序排列。
- [x] `FundDetailPage` 前端测试通过。

## Out Of Scope

- 不修改行情数据获取、图表实现或接口。
- 不调整基金详情摘要区和其他页面布局。

## Technical Notes

- 仅调整 `frontend/src/pages/FundDetailPage.jsx` 的现有 Tab 配置。
- 在 `frontend/src/pages/FundDetailPage.test.jsx` 增加默认 Tab 与顺序回归断言。
