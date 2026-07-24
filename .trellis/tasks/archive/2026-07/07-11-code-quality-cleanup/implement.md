# 实施计划

- [x] 先补成本单价与前端时区失败测试。
- [x] 修复 ErrorCode 和时间格式化。
- [x] 将晚间净值服务改为统一 Instant 日期标签并更新测试。
- [x] 统一 5 个 Spring 组件的构造器注入。
- [x] 修正失效注释。
- [x] 安装并配置 ESLint、Vitest，修复存量 lint 问题。
- [x] 补全前后端 quality guidelines。
- [x] 运行前端 lint/test/build、后端完整测试、diff 和 Trellis 校验。

## 验证命令

```powershell
cd frontend
npm run lint
npm test -- --run
npm run build
cd ../backend
mvn test
cd ..
git diff --check
python ./.trellis/scripts/task.py validate 07-11-code-quality-cleanup
```
