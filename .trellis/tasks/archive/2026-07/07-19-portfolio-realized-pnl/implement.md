# v0.9 执行清单

1. 定义组合收益明细 DTO 和单基金收益投影。
2. 聚合 CONFIRMED 交易的投入、赎回、费用及 FIFO 已实现收益。
3. 合并当前持仓未实现收益，并保留 CLEARED 基金历史行。
4. 处理转换两腿的组合层抵消。
5. 总览展示收益拆分和累计收益率。
6. 增加按基金收益明细表。
7. 补充普通买入、定投、转换、卖出、初始持仓、清仓和缺失净值测试。

## 验证

- `cd backend; mvn test`
- `cd frontend; npm run lint; npm test; npm run build`
