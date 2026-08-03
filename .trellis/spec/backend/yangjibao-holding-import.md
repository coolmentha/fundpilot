# Yangjibao Holding Import

## 1. Scope / Trigger

适用于养基宝二维码登录、账户持仓预览与导入。该集成只做用户主动导入，不进入行情源、后台任务或长期同步。

## 2. Signatures

- `POST /api/imports/yangjibao/sessions`
- `GET /api/imports/yangjibao/sessions/{id}`
- `GET /api/imports/yangjibao/sessions/{id}/preview`
- `POST /api/imports/yangjibao/sessions/{id}/import`
- `GET /api/imports/yangjibao/sessions/{id}/import`
- `POST /api/imports/yangjibao/sessions/{id}/import/retry`
- `DELETE /api/imports/yangjibao/sessions/{id}`
- `FundTransactionService.adjustToHoldingShares(Long, BigDecimal)`
- `MarketIndicatorRefreshApi.refreshOne(RefreshTarget)` for a new imported product

## 3. Contracts

- `/qr_code` 与 `/qr_code_state/{id}` 使用空 Token 计算 `MD5(path + timestamp + secret)`；JDK 客户端省略空 `Authorization` Header。
- 登录接口使用 `MD5(path + token + timestamp + secret)`，path 不含 query。
- Token 只存后端 30 分钟内存会话，完成、取消、超时清除。
- 会话绑定创建它的当前用户；其他用户即使取得 session ID 也按会话不存在处理。异步导入必须恢复创建者身份，使基金查找、新建和份额调整始终落在该用户的数据边界内。
- Spring Boot 4 客户端注入 `tools.jackson.databind.ObjectMapper`；不得使用 Flyway 间接依赖的 Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`，后者没有 Boot 自动配置 Bean。
- `YangjibaoClient` 使用 `RestClient.builder()` 构建专用客户端，不依赖生产上下文未提供的 `RestClient.Builder` Bean。
- 提交只接收预览 item ID 与 `KEEP_LOCAL/SYNC_TARGET`，份额和成本以服务端快照为准。
- 同 code 多账户最多选择一份。新 code 复用初始持仓；已有 code 显式选择保留或按锁后差额生成 ADJUST。
- 新 code 在 `PortfolioFundOnboardingApi.onboard(...)` 前必须调用 `MarketIndicatorRefreshApi.refreshOne(...)`，使用导入代码、名称和 `legacyFundId = null` 发布净值；不得假设该产品已有历史净值。
- `importing/package-info.java` 必须声明 `marketdata::api`；导入对行情刷新 API 的同步依赖是建仓前置条件，不能改为异步事件。
- 批量导入异步执行，提交立即返回任务状态；前端轮询进度，失败项可单独重试。
- 导入任务响应固定包含 `status/total/processed/succeeded/failed/currentFund/results`；`status` 仅为 `PROCESSING/COMPLETED`。
- 任务状态仍存于 30 分钟内存会话；应用重启或会话过期后不提供恢复，用户需重新扫码。

## 4. Validation & Error Matrix

| 条件 | 结果 |
|---|---|
| 会话不存在/过期 | `YANGJIBAO_SESSION_NOT_FOUND/INVALID` |
| 同 code 选择多份或未知 item | `YANGJIBAO_SESSION_INVALID` |
| 已有基金未选处理方式 | 该项 `YANGJIBAO_IMPORT_INVALID` |
| 导入未开始却查询进度 | `YANGJIBAO_SESSION_INVALID` |
| 导入未完成或无失败项却重试 | `YANGJIBAO_SESSION_INVALID` |
| 外部超时、业务码失败、解析失败 | `YANGJIBAO_API_FAILED`，不回显 Token/签名 |
| 新 code 刷新后仍无已公布净值 | 该项建仓失败并保留为可重试的失败项 |

## 5. Good / Base / Bad Cases

- Good：两个账户同 code，选择其中一份并明确同步目标份额。
- Base：已有基金选择 `KEEP_LOCAL`，返回跳过且不写库。
- Bad：前端回传自造份额，后端忽略并只使用会话预览快照。
- Good：本地尚无该基金净值时，先刷新并发布净值，再按导入的事实份额创建已确认期初持仓。
- Good：批量任务返回后持续轮询，完成时 `processed == total`；仅失败项进入重试任务。

## 6. Tests Required

- 两种签名与 query 剥离；二维码 Header 与响应解析。
- 轻量 Spring 上下文只加载 Boot Jackson 3 时必须能装配 `YangjibaoClient`，不得在测试中额外加载生产环境没有的 RestClient 自动配置。
- 扫码、预览、新基金导入、同 code 多选拒绝。
- 新基金导入必须断言行情刷新先于 onboarding，且 `RefreshTarget` 使用导入代码、名称和产品 ID。
- 目标份额正差、负差、零差和并发锁后重算。
- 前端未选处理方式不可提交、同 code 选择互斥。
- 异步任务立即返回、进度单调递增、失败项重试不重复执行成功项。

## 7. Wrong vs Correct

错误：让批量 HTTP 请求同步等待全部基金完成；把 Token 返回前端或持久化；在导入编排层直接写交易/lot；新基金直接 onboarding 而不刷新净值；使用预览时旧份额计算差额；客户端注入 Jackson 2 mapper。

正确：提交立即返回内存任务并轮询进度；Token 留在短期会话；新基金先通过现有行情刷新 API 发布净值，再调用 onboarding；提交时在交易事务锁内重算差额；客户端注入 Boot 管理的 Jackson 3 mapper。
