# Yangjibao Holding Import

## 1. Scope / Trigger

适用于养基宝二维码登录、账户持仓预览与导入。该集成只做用户主动导入，不进入行情源、后台任务或长期同步。

## 2. Signatures

- `POST /api/imports/yangjibao/sessions`
- `GET /api/imports/yangjibao/sessions/{id}`
- `GET /api/imports/yangjibao/sessions/{id}/preview`
- `POST /api/imports/yangjibao/sessions/{id}/import`
- `DELETE /api/imports/yangjibao/sessions/{id}`
- `FundTransactionService.adjustToHoldingShares(Long, BigDecimal)`

## 3. Contracts

- `/qr_code` 与 `/qr_code_state/{id}` 使用空 Token 计算 `MD5(path + timestamp + secret)`；JDK 客户端省略空 `Authorization` Header。
- 登录接口使用 `MD5(MD5(path) + token + timestamp + secret)`，path 不含 query。
- Token 只存后端 30 分钟内存会话，完成、取消、超时清除。
- Spring Boot 4 客户端注入 `tools.jackson.databind.ObjectMapper`；不得使用 Flyway 间接依赖的 Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`，后者没有 Boot 自动配置 Bean。
- 提交只接收预览 item ID 与 `KEEP_LOCAL/SYNC_TARGET`，份额和成本以服务端快照为准。
- 同 code 多账户最多选择一份。新 code 复用初始持仓；已有 code 显式选择保留或按锁后差额生成 ADJUST。

## 4. Validation & Error Matrix

| 条件 | 结果 |
|---|---|
| 会话不存在/过期 | `YANGJIBAO_SESSION_NOT_FOUND/INVALID` |
| 同 code 选择多份或未知 item | `YANGJIBAO_SESSION_INVALID` |
| 已有基金未选处理方式 | 该项 `YANGJIBAO_IMPORT_INVALID` |
| 外部超时、业务码失败、解析失败 | `YANGJIBAO_API_FAILED`，不回显 Token/签名 |

## 5. Good / Base / Bad Cases

- Good：两个账户同 code，选择其中一份并明确同步目标份额。
- Base：已有基金选择 `KEEP_LOCAL`，返回跳过且不写库。
- Bad：前端回传自造份额，后端忽略并只使用会话预览快照。

## 6. Tests Required

- 两种签名与 query 剥离；二维码 Header 与响应解析。
- 轻量 Spring 上下文必须能同时装配 Boot Jackson 3、RestClient 与 `YangjibaoClient`。
- 扫码、预览、新基金导入、同 code 多选拒绝。
- 目标份额正差、负差、零差和并发锁后重算。
- 前端未选处理方式不可提交、同 code 选择互斥。

## 7. Wrong vs Correct

错误：把 Token 返回前端或持久化；在导入编排层直接写交易/lot；使用预览时旧份额计算差额；客户端注入 Jackson 2 mapper。

正确：Token 留在短期会话；调用现有 Fund/Transaction Service；提交时在交易事务锁内重算差额；客户端注入 Boot 管理的 Jackson 3 mapper。
