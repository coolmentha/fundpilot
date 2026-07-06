# Design: 定投配置

## 架构与边界

定投是"自动执行"非"建议信号"——配置一次后定时 Job 自动生成 INVEST 交易,完全不经过 SignalLog/SignalType 系统。与卖出纪律(移动止盈/逻辑止损)解耦:定投只管买入,卖出交给现有信号引擎。

```
DcaSuggestionJob (14:55)
  └─ DcaPlanService.suggestForToday()
       ├─ 查所有 EFFECTIVE 定投计划
       ├─ TradingCalendarService.isTradingDay(today) 过滤
       ├─ 判定 today 是否计划日(周/月,月遇节假日顺延)
       └─ 命中 → FundTransaction(source=INVEST, amount, status=PENDING, dcaPlanId=plan.id)
                         ↓
DailyNavConfirmJob (20:00-22:00) 落当日净值
                         ↓
NavConfirmJob (次日凌晨 3:00) 确认 PENDING:查下单日净值 → 算 shares → CONFIRMED → 加权 costPerShare
```

## 数据模型

### fund_dca_plan 表 (V11 迁移)
```sql
CREATE TABLE fund_dca_plan (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    fund_id BIGINT NOT NULL REFERENCES fund(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    amount NUMERIC(19,8) NOT NULL,
    frequency VARCHAR(32) NOT NULL,        -- WEEKLY / MONTHLY
    day_of_week INT,                        -- 1-7(周一=1),WEEKLY 时必填
    day_of_month INT,                       -- 1-28,MONTHLY 时必填
    status VARCHAR(32) NOT NULL,            -- DRAFT / EFFECTIVE
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_date TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_fund_dca_plan_effective ON fund_dca_plan(fund_id) WHERE status='EFFECTIVE' AND deleted_date IS NULL;
```

### FundDcaPlanEntity
镜像 FundStrategyEntity 结构(Lombok @Getter/@Setter,extends AbstractEntity,软删 @SQLDelete)。
字段:fundEntity(ManyToOne)、enabled、amount、frequency(DcaFrequency 枚举)、dayOfWeek、dayOfMonth、status(DcaPlanStatus 枚举)。

### FundTransaction 加 dca_plan_id 字段
```sql
ALTER TABLE fund_transaction ADD COLUMN dca_plan_id BIGINT REFERENCES fund_dca_plan(id);
```
用于幂等去重(DcaSuggestionJob 查同日同 dca_plan_id 的 PENDING 交易是否存在)。

## DcaFrequency / DcaPlanStatus 枚举

```java
public enum DcaFrequency { WEEKLY, MONTHLY }
public enum DcaPlanStatus { DRAFT, EFFECTIVE }
```
不复用 StrategyParamStatus(语义不同:定投无 calibrate 流转,只有 DRAFT→activate→EFFECTIVE→retire→DRAFT)。

## DcaSuggestionJob 定投日判定

```java
// 1. 非交易日跳过
if (!tradingCalendarService.isTradingDay(today)) return;

// 2. 周定投:比对 day-of-week
if (plan.frequency == WEEKLY) {
    int dow = today.atZone(Asia/Shanghai).getDayOfWeek().getValue(); // 1=周一
    return dow == plan.dayOfWeek;
}

// 3. 月定投:比对 day-of-month,遇节假日顺延
if (plan.frequency == MONTHLY) {
    int dom = today.atZone(Asia/Shanghai).getDayOfMonth();
    if (dom == plan.dayOfMonth) return true;
    if (dom < plan.dayOfMonth) return false;
    // dom > plan.dayOfMonth:计划日已过,检查计划日是否非交易日(顺延到今天)
    // 查 [plan.dayOfMonth, today] 区间内是否有非交易日,且今天是区间内第一个交易日
    return isPostponedFrom(plan.dayOfMonth, today);
}
```

`isPostponedFrom(planDay, today)`:计划日 planDay 非交易日 → 顺延;若今天是 planDay 之后第一个交易日,且 planDay..today-1 全是非交易日,则今天补执行。

## 幂等去重

```java
// 生成前查同日同计划是否已有 PENDING 交易
boolean exists = fundTransactionRepository
    .existsByFundEntity_IdAndDcaPlanIdAndStatusAndCreatedDateBetween(
        fundId, planId, PENDING, todayStart, todayEnd);
if (exists) return; // 已生成,跳过
```

## NavConfirmJob 时序调整

cron `0 0 21 * * MON-FRI` → `0 0 3 * * MON-FRI`。
- 凌晨 3 点确认的是昨天及更早的 PENDING 交易。
- NavConfirmService 逻辑不变:查 PENDING 交易 → 按 transaction.createdDate 找下单日已落库净值 → 算 shares = amount / nav → CONFIRMED → 加权 costPerShare。
- DailyNavConfirmJob(20:00-22:00 落当日净值)不动——它保证下单日净值在次日凌晨 3 点前已落库。

## DcaPlanService 状态机

```
DRAFT --activate--> EFFECTIVE --retire--> DRAFT
```
- createDraft:新建 status=DRAFT。
- updateDraft:仅 DRAFT 可改(改 enabled/amount/frequency/day)。
- activate:DRAFT→EFFECTIVE,回退同基金旧 EFFECTIVE→DRAFT。
- retire:EFFECTIVE→DRAFT。

## 前端

- `FundDetailPage.jsx` items 加第 5 项:`{key:'dca', label:'定投计划', children:<FundDcaTab fundId={id}/>}`。
- `FundDcaTab.jsx`(拷贝 FundStrategyTab 结构):列表(金额/频率/定投日/状态)+ 新建/编辑/激活/停用。
- `DcaPlanFormModal.jsx`(拷贝 StrategyFormModal):amount/frequency/dayOfWeek/dayOfMonth(frequency=WEEKLY 显示 dayOfWeek,MONTHLY 显示 dayOfMonth)。
- `hooks.js` 加 useDcaPlans/useActiveDcaPlan/useCreateDcaPlan/useUpdateDcaPlan/useDcaPlanAction。

## 兼容性

- 新增表 + 新增字段,不影响存量数据。
- NavConfirmJob 时序改:21:00 改 3:00,存量 PENDING 交易在 3:00 确认(延迟 6 小时,可接受——交易确认非实时需求)。
- 手动 INVEST 交易不受影响(dca_plan_id=null)。

## 风险

1. **月定投节假日顺延逻辑复杂**:需仔细测试跨节假日场景(如国庆 7 天)。补 `TradingCalendarService.findNextTradingDay` 或在 Job 内联判定。
2. **NavConfirmJob 延迟 6 小时**:用户当晚看不到确认份额,次日凌晨才确认。可接受(交易确认非实时)。若用户反馈,可保留 21:00 + 加 3:00 两次确认。
3. **幂等去重依赖 dca_plan_id**:手动 INVEST 交易 dca_plan_id=null,不会被误判为定投重复。
