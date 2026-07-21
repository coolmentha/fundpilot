# 养基宝持仓导入技术设计

## 边界

本功能只负责把养基宝账户持仓转换为 FundPilot 已有业务动作，不建立长期同步关系。后端新增养基宝 API 客户端、短期导入会话与导入编排；前端在设置页提供扫码、预览、选择和结果展示。无数据库迁移、无新依赖、无后台任务，不修改行情数据源链。

## 后端结构

### 养基宝客户端

- 使用项目已有 Spring Cloud OpenFeign（或现有等价 HTTP 封装），Base URL 配置化，默认读取文档中的 HTTP 地址。
- 二维码接口使用匿名签名：空 Token，`MD5(path + TsSec + secret)`。
- 登录后接口使用通用签名：`MD5(MD5(path) + token + TsSec + secret)`；签名 path 不含 query string。
- Token、签名和完整外部响应不写日志；错误只保留接口名、HTTP 状态、养基宝业务码和会话 ID。
- 复用现有依赖与超时配置方式，不新增 SDK 或二维码依赖。前端直接展示接口返回的二维码 URL。

### 导入会话

- 后端内存保存 `sessionId -> {state, token, qrId, expiresAt, previewSnapshot, submitted}`，TTL 30 分钟。
- 前端只得到随机 `sessionId`、二维码 URL、状态和脱敏持仓预览，不接触 Token。
- 状态为 `WAITING / CONNECTED / COMPLETED / CANCELLED / EXPIRED`。
- 应用重启会话自然失效；完成、取消或超时立即清除 Token 和预览快照。
- 会话操作串行化，提交只能成功进入一次；重复提交返回首次结果或明确的已完成状态，不重复写账。

### 预览

1. 创建会话并返回二维码 URL。
2. 前端按有限间隔查询 FundPilot 后端的会话状态；后端再查询养基宝扫码状态。
3. 扫码成功后，后端保存 Token，并按账户读取 `/user_account` 和 `/fund_hold?account_id=...`。
4. 预览项使用养基宝持仓 `id` 作为会话内 item ID，包含账户 ID/名称、基金 code/name、`hold_share`、`hold_cost`。
5. 后端按基金 code 匹配当前未删除 FundPilot 基金并计算当前事实份额与差额。
6. 同 code 跨账户独立展示，前后端都校验同一 code 最多选择一个 item ID。

### 导入动作

- 新 code：调用现有 `FundService.create`，传 `initialHoldingShares=hold_share`、`costPerShare=hold_cost`，类型字段缺省时沿用现有名称分类兜底；该调用自身保证基金、净值和初始 `INCREASE` 交易原子性。
- 已有 code + “以本系统为准”：返回跳过结果，不写数据库。
- 已有 code + “同步为养基宝份额”：锁后重新读取当前 CONFIRMED 事实份额，以两位份额精度计算差额。正差调用现有手动 `ADJUST_IN`，负差调用 `ADJUST_OUT`，零差跳过。
- 每项使用现有业务 Service 的事务边界独立执行；一项失败不回滚其他成功项。编排层不直接操作 Entity、Repository 或 lot。
- 提交只接受预览快照中的 item ID 和处理方式，不接受前端回传的基金代码、份额或成本作为事实源，防止预览后篡改。

## FundPilot API

建议统一放在 `/api/imports/yangjibao`：

- `POST /sessions`：创建二维码会话。
- `GET /sessions/{sessionId}`：查询扫码/会话状态。
- `GET /sessions/{sessionId}/preview`：读取账户和持仓预览。
- `POST /sessions/{sessionId}/import`：提交选择与已存在基金处理方式，返回逐项结果。
- `DELETE /sessions/{sessionId}`：取消并清理会话。

Controller 只做路由和 DTO 转换，业务逻辑下沉 Service；错误使用 `ErrorCode` 与全局异常处理。

## 前端交互

- 入口位于设置页“持仓导入”，点击后打开导入 Modal。
- 扫码阶段显示二维码和等待/过期/失败状态；过期后可重新生成。
- 预览按账户分组展示。每行有选择框；同 code 选择一行后，其他账户版本禁用并提示已选择来源。
- 新基金显示“新增基金”；已有基金提供“以本系统为准/同步为养基宝份额”单选项，并展示两侧份额和预计差额；勾选已有基金后必须明确选择一种处理方式。
- 提交后展示逐项成功、跳过、失败结果；成功后刷新基金、交易和组合相关查询。
- Token 和外部二维码登录 ID 不写 localStorage/sessionStorage。

## 兼容与回滚

- 不改 schema、现有公共 API 或已有数据，关闭/删除新入口即可回滚。
- 养基宝不可用只影响用户主动导入，不影响基金列表、交易、净值刷新和后台任务。
- Base URL 与 secret 使用后端配置；secret 不下发前端。生产环境可覆盖地址，避免写入机器专属配置。

## 关键测试

- 两种签名公式、path 去 query、时间戳和敏感信息脱敏。
- 会话状态、过期/取消/完成清理和重复提交。
- 同 code 跨账户互斥选择。
- 新基金复用初始持仓；已有基金的正差、负差、零差和保留本系统。
- 单项失败隔离、预览篡改拒绝、并发提交幂等。
- 设置页扫码、预览选择、默认保留本系统、结果展示及查询失效。
