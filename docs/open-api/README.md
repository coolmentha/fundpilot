# FundPilot Open API

FundPilot Open API 是面向当前用户的只读接口。调用方使用平台签发的 Bearer Token 访问，不需要账号密码，也不能使用浏览器会话 Cookie 或兼容管理密钥。

## 鉴权

1. 登录 FundPilot，在「用户配置」或「Open API」页面创建 Token。
2. 对 `GET` 请求添加请求头：

```http
Authorization: Bearer fp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

3. Token 只绑定当前用户；接口只返回该用户有权查看的数据。Open API 不提供任何写操作或后台管理能力。

未携带 Token、Token 格式错误、Token 已被刷新/撤销，或所属用户已停用，统一返回 `HTTP 401`。

## Token 生命周期

- 每个用户同时只有一个有效 Token。
- 创建时明文只返回一次；服务端仅保存 SHA-256 摘要和元数据。
- **刷新即轮换**：生成新 Token 并立即替换旧 Token，无需等待。
- 撤销会删除当前有效 Token。
- 刷新或撤销后，需要同步更新所有调用方持有的旧 Token。

## 公开发现入口

`GET /api/open` 无需 Token，返回版本、鉴权方式和资源范围，便于客户端探测兼容性。

## v1 只读接口

所有路径均以 `/api/open/v1` 为前缀。

| 资源 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 市场 | GET | `/market/status` | A 股交易状态与数据时间 |
| 市场 | GET | `/market/indices` | 当前用户关注指数的实时行情 |
| 市场 | GET | `/market/breadth` | 沪深京涨跌、涨跌停家数 |
| 市场 | GET | `/market/sectors` | 行业板块涨跌排行 |
| 市场 | GET | `/market/volume-price` | 上证指数量价状态 |
| 基金 | GET | `/portfolio-funds` | 当前用户组合基金与分组 |
| 基金 | GET | `/portfolio-funds/{id}` | 单只组合基金 |
| 收益 | GET | `/portfolio/summary` | 持仓汇总、当日盈亏与盈亏基金数 |
| 收益 | GET | `/portfolio/returns` | 累计收益与逐基金收益 |
| 收益 | GET | `/portfolio/returns/trends?period=30D` | 收益趋势；可选 `from`/`to` |
| 交易 | GET | `/portfolio-funds/{id}/transactions` | 组合基金交易流水 |
| 交易 | GET | `/transactions/pending` | 待确认交易 |
| 定投 | GET | `/investment-plans` | 定投计划列表 |
| 定投 | GET | `/investment-plans/{portfolioFundId}` | 按组合基金查询计划 |
| 提醒 | GET | `/alerts/rules` | 提醒规则 |
| 提醒 | GET | `/alerts/notifications?limit=100` | 最近提醒记录 |
| 身份 | GET | `/me` | 当前 Token 的所属用户 ID |

## 示例

```bash
curl -H "Authorization: Bearer $FUNDPILOT_TOKEN" \
  http://localhost:8080/api/open/v1/portfolio/summary
```

## 响应格式

接口沿用平台统一响应包装：

```json
{
  "success": true,
  "data": {},
  "code": null,
  "message": null
}
```

成功时读取 `data`；鉴权失败时 HTTP 状态为 `401`，响应体中的 `code` 为 `ADMIN_UNAUTHORIZED`，但不区分 Token 不存在、已撤销、已刷新或账号停用。
