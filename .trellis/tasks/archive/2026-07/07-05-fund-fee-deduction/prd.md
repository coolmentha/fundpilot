# 基金交易手续费扣除(申购费+赎回费+FIFO)

## Goal

让 FundPilot 的交易流水反映真实手续费成本:买入/定投自动扣申购费,卖出按持有期 FIFO 匹配扣赎回费。当前系统是毛额记账(`shares = amount/nav`、`amount = shares*nav`),导致份额/金额/持仓成本系统性偏高,长期定投累积误差明显。费率从天天基金 `jjfl_<code>.html` 自动爬取,用户无需手动填。

## Background

- 用户问"定投 买入等流水有扣除手续费吗",调研确认:**没有**。`FundTransactionEntity` 无 fee 字段,后端 grep `手续费|fee|commission|charge` 零匹配。
- 当前架构是纯聚合:`FundEntity.costPerShare`(单一加权均价)+ `holdingShares = Σ tx.shares × dir`(实时算)。**无 lot/FIFO/realized P/L**。
- 买入回填 `NavConfirmService.java:88` `shares = amount/nav`;卖出 `:95` `amount = shares*nav`。两条确认路径(`NavConfirmService` 批量 + `TransactionConfirmService` 单笔)逻辑重复。
- 天天基金费率页 `http://fundf10.eastmoney.com/jjfl_<code>.html` 是 HTML 服务端渲染(无 JSON 接口),含:申购费率(原费率 | 天天基金优惠费率,1折)、赎回费率阶梯(按持有期)、销售服务费(C类)。

## Confirmed Facts

- 优惠费率(0.12%-0.15%)覆盖天天基金/支付宝/蛋卷等主流平台;银行柜台不打折(1.5%)场景不支持。
- 赎回费率阶梯典型:<7天 1.5% / 7-30天 0.75% / 30-365天 0.5% / 1-2年 0.25% / ≥2年 0%。持有期 = 卖出确认日 − 买入确认日(自然日)。
- C类基金:无申购费(0%),有销售服务费(年化,已在净值扣,不单独算),赎回费阶梯短(<7天 1.5%,≥7天 0%)。
- 管理费/托管费已在净值里扣,不单独计算。
- Flyway 下一版本 V13(V5/V7 被占用,禁用)。
- 买入 tx 的 `confirmTime` 是 lot 持有期锚点(用户可 back-date 初始持仓 `FundService.openWithExistingPosition`,所以用 confirmTime 不用 createdDate)。

## Requirements

### R1 费率自动获取
- R1.1 爬取 `jjfl_<code>.html`,解析出:申购费率(原 + 优惠)、赎回费率阶梯(List)、销售服务费。
- R1.2 存 `fund_fee` 表(按 fund_code 唯一),每日 06:30 定时刷新 + 启动预热。
- R1.3 爬取失败/页面无费率 → 返 null,调用方降级为不扣费(fee=0)+ warn 日志,不阻断交易。
- R1.4 费率缓存查询走 DB(费率慢变,不用内存 volatile 缓存)。

### R2 申购费扣除(买入/定投)
- R2.1 买入类(INCREASE/TRANSFER_IN/INVEST)确认时:`fee = amount × discountRate; netAmount = amount − fee; shares = netAmount / nav`。
- R2.2 费率缺失 → fee=0,按原逻辑(毛额)。
- R2.3 创建 `fund_lot` 记录:`acquireDate = confirmTime`、`acquireShares = shares`、`remainingShares = shares`、`acquireCostPerShare = netAmount / shares`。
- R2.4 持仓成本更新用 `netAmount`(扣费后),公式不变(加权均价)。
- R2.5 定投(INVEST)同买入逻辑。

### R3 赎回费扣除(卖出)+ FIFO
- R3.1 卖出类(DECREASE/TRANSFER_OUT)确认时:按 `acquireDate ASC` 遍历 `fund_lot`(`remainingShares > 0`),FIFO 消耗份额。
- R3.2 每个 lot 算 `holdingDays = (sellConfirmDate − acquireDate).toDays()`,查赎回费率阶梯得 `rate`。
- R3.3 `fee = Σ lot.sharesConsumed × nav × rate; amount = shares × nav − fee`。
- R3.4 记录 `fund_lot_redemption`(每 lot 一行:`sharesConsumed`、`holdingDays`、`rate`)。
- R3.5 扣减 `lot.remainingShares`;若卖超(总剩余不足),抛业务异常拦截。
- R3.6 赎回费率缺失 → rate=0(不扣赎回费),仍记录 lot 消耗。

### R4 确认路径统一
- R4.1 抽 `TransactionConfirmSupport` 共享 helper,`NavConfirmService` + `TransactionConfirmService` 都调它,消除买入扣费/建仓、卖出 FIFO/扣费、成本更新的重复逻辑。

### R5 前端展示
- R5.1 流水表 `FundTransactionTab.jsx` 加「手续费」列(`money(fee)`,null → '-')。
- R5.2 详情页 `FundDetailPage.jsx` 加「参考费率」展示(申购优惠费率 + 赎回费率阶梯简表)。
- R5.3 `FundTransactionView` 加 `fee`、`feeRate` 字段。
- R5.4 新增 `GET /api/funds/{id}/fee-rates` endpoint + `useFundFeeRates(fundId)` hook。

### R6 历史数据迁移
- R6.1 已确认的买入交易回填 `fund_lot`(`acquireDate = confirmTime`、`acquireShares = shares`、`remainingShares` 按后续卖出比例扣减或粗略全留)。
- R6.2 **历史卖出交易不回溯扣赎回费**,保持历史 amount 不变。
- R6.3 历史 `FundEntity.costPerShare` 不重算(保持连续性)。

## Acceptance Criteria

- [ ] AC1 爬取 001071 能解析出申购优惠费率 0.15% + 赎回阶梯[<7天 1.5%, 7-30天 0.75%, 30-365天 0.5%, 1-2年 0.25%, ≥2年 0%]
- [ ] AC2 买入 1000 元,nav=1.5,discountRate=0.0015 → fee=1.50,shares=(1000−1.50)/1.5=665.6667,lot 创建
- [ ] AC3 卖出 100 份,nav=1.6,持有 5 天(赎回率 1.5%)→ fee=100×1.6×0.015=2.40,amount=100×1.6−2.40=157.60,lot_redemption 记录
- [ ] AC4 卖出跨多 lot(FIFO):卖 200 份,lot A 剩 150(持有 10 天,0.75%)+ lot B 剩 100(持有 100 天,0.5%)→ 消耗 A 150 + B 50,fee = 150×1.6×0.0075 + 50×1.6×0.005
- [ ] AC5 费率缺失(fund_fee 无记录)→ 买入 fee=0 按毛额,卖出 rate=0,记 warn,不阻断
- [ ] AC6 流水表显示「手续费」列,数值格式 CNY 2dp,null 显示 '-'
- [ ] AC7 详情页显示参考费率(申购率 + 赎回阶梯)
- [ ] AC8 历史买入交易回填 lot 后,新卖出能 FIFO 匹配
- [ ] AC9 `./mvnw test` 全绿(新增爬取解析单测 + FIFO 匹配单测 + 扣费公式单测)
- [ ] AC10 NavConfirmService + TransactionConfirmService 共用 TransactionConfirmSupport,无重复逻辑

## Out of Scope

- 后端收费模式(后端申购费,按持有期递减)—— 测试基金均无此后端表,遇不到
- 银行柜台非打折费率(1.5%)—— 用户场景不在银行买
- 认购费(新基金发行期)—— FundPilot 不跟踪新发基金
- 管理费/托管费 —— 已在净值里扣
- 已实现盈亏(realized P/L)单独报表 —— 本次只记 fee,realized P/L 留后续
- 历史卖出交易回溯扣赎回费 —— 只对新交易扣

## Open Questions

无。所有决策用默认值:
- HTML 解析加 Jsoup 依赖(正则脆弱)
- 历史卖出不回溯
- 费率缺失降级不扣费
- 优惠费率口径用天天基金(1折)
- 参考费率独立 endpoint(不嵌入 FundView)
