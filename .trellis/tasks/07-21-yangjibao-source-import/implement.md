# 养基宝持仓导入实施计划

## 实施顺序

1. 后端外部客户端
   - 增加养基宝配置、响应 DTO、匿名/登录签名器和客户端适配层。
   - 使用 MockWebServer 覆盖请求路径、请求头、两种签名和错误映射。

2. 后端短期会话与预览
   - 实现 30 分钟内存会话、状态流转、Token 清理和扫码状态查询。
   - 拉取账户/持仓，匹配本地基金，形成不可由前端改写的预览快照。
   - 覆盖过期、取消、Token 失效、同 code 多账户场景。

3. 后端导入编排
   - 新基金调用 `FundService.create` 的初始持仓路径。
   - 已有基金按用户选择跳过或基于锁后事实份额调用 `ADJUST_IN/ADJUST_OUT`。
   - 实现逐项结果、同 code 互斥、单项事务隔离与重复提交保护。
   - 增加 Controller 与 API DTO，不在 Controller 编写业务逻辑。

4. 前端导入界面
   - 在设置页加入持仓导入入口和 Modal。
   - 增加会话创建、状态轮询、预览、同 code 互斥选择、已有基金处理方式及提交结果。
   - 成功后失效基金、交易、组合相关 React Query 缓存。

5. 回归与文档
   - 验证现有基金创建、调整流水、lot、设置页和行情刷新不受影响。
   - 仅在实现发现稳定的新契约时更新 `.trellis/spec/`，不扩大本任务范围。

## 预计影响文件

- `backend/src/main/java/com/fundpilot/backend/`：新增养基宝导入模块，并小范围复用 fund Service。
- `backend/src/test/java/com/fundpilot/backend/`：客户端、会话、编排与 Controller 测试。
- `frontend/src/api/hooks.js`：导入 API hooks 与缓存失效。
- `frontend/src/pages/SettingsPage.jsx`：导入入口。
- `frontend/src/components/`：独立导入 Modal 及测试。
- 后端配置文件：仅增加可覆盖的 Base URL、secret 和超时/TTL 配置；无数据库迁移。

## 验证命令

```powershell
cd backend
./mvnw.cmd test
./mvnw.cmd verify

cd ../frontend
npm test
npm run lint
npm run build
```

浏览器验证：登录后进入设置页，完成生成二维码、扫码成功、跨账户同 code 互斥、已有基金两种处理方式、部分失败结果展示，并确认基金与交易页面刷新正确。

## 风险与回滚点

- 外部签名变化：签名器与客户端独立，回滚该模块不影响行情源。
- 批量部分成功：结果逐项展示，不做跨基金大事务；失败项可重新发起导入会话。
- 并发份额变化：提交时锁后重算差额，预览仅供确认，不作为最终当前份额。
- 内存会话丢失：用户重新扫码，无持久化迁移或数据修复成本。
- 用户已有改动：`api文档.md` 当前含用户修改，实施时只保留并增量调整相关签名说明，不覆盖其他内容。

## 开始实施前门禁

- 用户审核并确认 `prd.md`、`design.md`、`implement.md`。
- 获得明确许可后，从主分支创建并切换 `feature/yangjibao-holding-import`。
- 运行 `task.py start`，再按 `trellis-before-dev` 读取相关 backend/frontend 规范后编码。
