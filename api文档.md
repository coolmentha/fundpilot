# 养基宝 API 接口文档

## 基础信息

| 项目 | 值 |
|------|-----|
| Base URL | `http://browser-plug-api.yangjibao.com` |
| Content-Type | `application/json;charset=UTF-8` |
| Timeout | 15s |
| Token 存储 | `chrome.storage.local` → key `"token"` |

## 签名算法

```
Request-Sign = MD5( path + token + TsSec + "YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc" )
```

| 参数 | 说明 |
|------|------|
| `path` | 不含 query string 的路径 (如 `/account_collect`) |
| `token` | 登录凭证 |
| `TsSec` | 秒级 Unix 时间戳 `Math.floor(Date.now()/1000)` |

### 请求头

```
Content-Type: application/json;charset=UTF-8
Authorization: {token}
Request-Time: {TsSec}
Request-Sign: {sign}
```

### 二维码公开接口

`/qr_code` 与 `/qr_code_state/{id}` 不需要登录态，使用与通用接口相同的签名公式，但 `token` 取空字符串 (`""`)：

```text
Authorization: <空字符串>
Request-Time: {TsSec}
Request-Sign: MD5(path + "" + TsSec + secret)
```

例如：

```text
MD5("/qr_code" + "" + TsSec + "YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc")
```

### 通用响应格式

```json
{
  "code": 200,
  "data": {},
  "message": "ok"
}
```
`code === 200` 成功，`data` 为业务数据。401 触发自动登出。

---

## 接口列表

### 1. 获取持仓汇总

```
GET /account_collect
```

**响应 `data`:**

```json
{
  "today_income": 123.45,
  "today_income_rate": 0.56,
  "assets_collect": 150000.00,
  "is_single_account_model": true,
  "account_data": [
    {
      "account_id": "xxx",
      "title": "支付宝",
      "today_income": 100.00,
      "up": 3,
      "down": 1,
      "hold_income": 500.00,
      "hold_cost": 10000.00
    }
  ],
  "index_data": {
    "1.000001": { "code": "000001", "v": "3200.00", "dir": "1.23", "div": "-0.15" },
    "0.399001": { "code": "399001", "v": "10800.00", "dir": "-0.50", "div": "0.20" },
    "0.399006": { "code": "399006", "v": "2200.00", "dir": "2.10", "div": "0.05" },
    "1.000300": { "code": "000300", "v": "4200.00", "dir": "0.80", "div": "0.10" },
    "1.000016": { "code": "000016", "v": "2800.00", "dir": "-0.30", "div": "0.00" }
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `today_income` | number | 汇总当日收益 |
| `today_income_rate` | number | 汇总当日收益率 |
| `assets_collect` | number | 汇总总资产 |
| `is_single_account_model` | boolean | 单/多账户模式 |
| `account_data` | array | 各账户数据 |
| `account_data[].account_id` | string | 账户 ID |
| `account_data[].title` | string | 账户名称 |
| `account_data[].today_income` | number | 该账户当日收益 |
| `account_data[].up` | number | 上涨基金数 |
| `account_data[].down` | number | 下跌基金数 |
| `account_data[].hold_income` | number | 持仓收益 |
| `account_data[].hold_cost` | number | 持仓成本 |
| `index_data` | object | 大盘指数数据 |

---

### 2. 基金持仓明细

```
GET /fund_hold?account_id={id}
```

**响应 `data`:** (数组)

```json
[
  {
    "id": "xxx",
    "code": "000001",
    "fund_id": "yyy",
    "short_name": "易方达蓝筹精选",
    "hold_share": "1000.50",
    "hold_cost": "1.2000",
    "has_aip": false,
    "has_up_down_remid": false,
    "is_fuzzy": false,
    "nv_info": {
      "gsz": "1.3500",
      "gszzl": "2.50",
      "dwjz": "1.3200",
      "rzzl": "1.80",
      "gztime": "2024-01-15 14:30:00",
      "qjgzrq": "2024-01-14",
      "zxjzrq": "2024-01-13",
      "net_time": "2024-01-14",
      "true_valuation_date": "2024-01-14"
    }
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 持仓记录 ID |
| `code` | string | 基金代码 |
| `fund_id` | string | 平台基金 ID |
| `short_name` | string | 基金简称 |
| `hold_share` | string | 持有份额 |
| `hold_cost` | string | 持仓成本/净值 |
| `has_aip` | boolean | 是否有定投 |
| `has_up_down_remid` | boolean | 是否有涨跌提醒 |
| `is_fuzzy` | boolean | 是否模糊匹配 |
| `nv_info` | object | 净值信息 |
| `nv_info.gsz` | string | 估算净值 |
| `nv_info.gszzl` | string | 估算涨跌幅 (%) |
| `nv_info.dwjz` | string | 最新单位净值 |
| `nv_info.rzzl` | string | 日增长率 (%) |
| `nv_info.gztime` | string | 估算时间 |
| `nv_info.qjgzrq` | string | 前次估值日期 |
| `nv_info.zxjzrq` | string | 最新净值日期 |
| `nv_info.net_time` | string | 净值时间 |
| `nv_info.true_valuation_date` | string | 真实估值日期 |

---

### 3. 添加/更新持仓

```
POST /fund_hold
```

**请求体:**

```json
{
  "items": [
    {
      "fund_id": "yyy",
      "fund_code": "000001",
      "hold_share": "1000.50",
      "hold_cost": "1.2000",
      "model": 1
    }
  ],
  "account_id": "xxx",
  "sync_optional": 0
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | array | 基金列表 |
| `items[].fund_id` | string | 平台基金 ID |
| `items[].fund_code` | string | 基金代码 |
| `items[].hold_share` | string | 持有份额 |
| `items[].hold_cost` | string | 持仓成本 |
| `items[].model` | number | 模式 (1) |
| `account_id` | string | 账户 ID |
| `sync_optional` | number | 同步选项 (0) |

**响应:** `code: 200` 成功，无特殊 data。

---

### 4. 删除持仓

```
DELETE /remove_fund_hold?fund_ids[]={id1}&fund_ids[]={id2}&account_id={xxx}
```

**响应:** `code: 200` 成功。

---

### 5. 账户收益

```
GET /income_data?account_id={id}
```

**响应 `data`:** beat 收益率值 (具体字段由 `r$(id)` 直接赋给 `D.value.beat.rate` 使用)。

---

### 6. 汇总收益

```
GET /income_data?collect=true
```

**响应 `data`:** 同上，汇总模式。

---

### 7. 收益走势图

```
GET /income_line_data?account_ids[]={id1}&account_ids[]={id2}&date_type=day
```

**响应 `data`:** 以 account_id 为 key 的对象:

```json
{
  "account_id_1": {
    "line_list": [
      { "rate": 1.23 },
      { "rate": -0.50 },
      { "rate": 2.10 }
    ]
  },
  "account_id_2": {
    "line_list": [
      { "rate": 0.80 },
      { "rate": -0.30 }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `{account_id}` | object | 以账户 ID 为 key |
| `.line_list` | array | 收益率时序数据 |
| `.line_list[].rate` | number | 该时间点的收益率 |

---

### 8. 大盘指数

```
GET /index_data
```

**响应 `data`:**

```json
{
  "1.000001": { "code": "000001", "v": "3200.00", "dir": "1.23", "div": "-0.15" },
  "1.000300": { "code": "000300", "v": "4200.00", "dir": "0.80", "div": "0.10" },
  "0.399001": { "code": "399001", "v": "10800.00", "dir": "-0.50", "div": "0.20" },
  "0.399006": { "code": "399006", "v": "2200.00", "dir": "2.10", "div": "0.05" },
  "1.000016": { "code": "000016", "v": "2800.00", "dir": "-0.30", "div": "0.00" }
}
```

| Key | 指数 |
|-----|------|
| `1.000001` | 上证指数 |
| `1.000300` | 沪深300 |
| `0.399001` | 深证成指 |
| `0.399006` | 创业板指 |
| `1.000016` | 上证50 |

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | string | 指数代码 |
| `v` | string | 当前点位 |
| `dir` | string | 涨跌幅 (%) |
| `div` | string | 振幅 (%) |

---

### 9. 用户账户

```
GET /user_account
GET /user_account?from={平台来源}
```

**响应 `data`:**

```json
{
  "list": [
    {
      "id": "xxx",
      "title": "支付宝"
    }
  ],
  "is_single_account_model": true,
  "target_account_id": "yyy"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `list` | array | 账户列表 |
| `list[].id` | string | 账户 ID |
| `list[].title` | string | 账户名称 |
| `is_single_account_model` | boolean | 单/多账户模式 |
| `target_account_id` | string | 目标账户 ID |

---

### 10. 生成导入二维码

```
GET /qr_code
```

**响应 `data`:**

```json
{
  "id": "qr_session_xxx",
  "url": "https://..."
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 二维码会话 ID |
| `url` | string | 二维码内容 URL |

---

### 11. 扫码状态查询

```
GET /qr_code_state/{id}
```

**响应 `data`:**

```json
{
  "state": "2",
  "token": "auth_token_xxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `state` | string | `"1"` 等待扫码, `"2"` 扫码成功, `"3"` 已过期 |
| `token` | string | 登录凭证 (state=2 时返回) |

---

### 12. 公告

```
GET /notice
```

**响应 `data`:**

```json
{
  "id": "xxx",
  "content": "系统公告内容...",
  "code": "announcement_code"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 公告 ID |
| `content` | string | 公告内容 |
| `code` | string | 跳转码 (拼接为 `{BASE_URL}/redirect/{code}`) |

---

### 13. 版本检查

```
GET /version_info
GET /version_info?version={当前版本号}
```

**响应 `data`:** (对象或数组)

```json
{
  "is_constraint": false,
  "version": "2.1.0",
  "...": "..."
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `is_constraint` | boolean | 是否强制更新 |
| `version` | string | 最新版本号 |

---

## 签名伪代码示例

```js
const BASE = "http://browser-plug-api.yangjibao.com";
const SECRET = "YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc";

function md5(s) { /* 标准 MD5 */ }
function sign(path, token) {
  const ep = path.includes("?") ? path.split("?")[0] : path;
  return md5("" + ep + token + Math.floor(Date.now() / 1000) + SECRET);
}

async function api(path, method = "GET", body = null) {
  const { token } = await chrome.storage.local.get("token");
  const headers = {
    "Content-Type": "application/json;charset=UTF-8",
    Authorization: token,
    "Request-Time": String(Math.floor(Date.now() / 1000)),
    "Request-Sign": sign(path, token),
  };
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  const json = await res.json();
  if (json.code !== 200) throw new Error(json.message);
  return json.data;
}
```
