# 实施计划

1. 新增统一两位份额函数和 V23 迁移，扩展 AccountingRebuild 重放为两位份额。
2. 在买入确认、初始持仓、手工/信号交易入口统一份额舍入。
3. 在事务 Repository 增加带锁读取，新增 PENDING 更新服务与 Controller 路由。
4. 补后端测试：两位份额、迁移重放、字段更新、非 PENDING 拒绝、转换日期同步、关联保持。
5. 前端请求层增加更新 mutation和共享编辑弹窗，在两个页面接入编辑与“全部”。
6. 补前端测试并同步交易一致性 spec、业务文档。

## 验证

```powershell
cd backend
mvn test

cd ../frontend
npm run lint
npm test
npm run build
```

## 风险与回滚点

- 编辑与确认竞争：必须在同一事务锁定并检查状态。
- 转换双腿：必须一次事务同步日期，禁止直接编辑派生腿。
- 全量卖出：只能冻结点击时原始份额，不能在确认时动态扩张。
