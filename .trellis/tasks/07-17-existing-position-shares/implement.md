# 已有持仓按份额录入实施计划

## 实施顺序

1. 更新领域/产品文档中的已有持仓口径。
2. 先更新后端测试，覆盖直接保存份额、金额派生和非正份额校验。
3. 替换 `FundCreateRequest` 字段并简化 `FundService` 建仓计算。
4. 替换前端表单字段、显示条件和请求载荷。
5. 检查全仓库不再存在有效的 `initialMarketValue` 引用。

## 验证

- `mvn -Dtest=FundServiceTest,FundServiceAutoFetchTest test`
- `npm test -- --run`
- `npm run lint`
- `npm run build`
- 检查 `git diff`，确认无 schema、依赖或无关格式化变更。

## 风险文件

- `backend/.../FundCreateRequest.java`：公共请求字段替换。
- `backend/.../FundService.java`：初始交易金额和份额口径。
- `frontend/src/pages/FundsPage.jsx`：表单字段与条件渲染。
