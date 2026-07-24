# 实施计划

- [x] 补信号重跑已回应场景回归测试并修复覆盖逻辑。
- [x] 补定投跨月顺延、周末非法、全状态幂等测试并修复。
- [x] 增加手动交易发生日字段、前端输入和历史净值确认测试。
- [x] 增加定投与信号操作后端正数/范围校验。
- [x] 新增未触发卖出原因并同步前端展示。
- [x] 修正交易和管理页面过期文案。
- [x] 更新 transaction consistency 规范。
- [x] 运行后端完整测试、前端 lint/test/build、diff 和 Trellis 校验。

## Validation

```powershell
cd backend
mvn test
cd ../frontend
npm run lint
npm test
npm run build
cd ..
git diff --check
python ./.trellis/scripts/task.py validate 07-11-function-correctness-fixes
```

## Result

- 后端：396 tests，0 failures，0 errors，0 skipped。
- 数据库：既有 V15 库升级到 V16 通过；全新空库从 V1 迁移到 V16 并通过 Hibernate validate。
- 前端：ESLint 通过；Vitest 3 tests 通过；Vite 生产构建通过（保留既有大 chunk 警告）。
