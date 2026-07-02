package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 基金新建/更新请求 DTO(ADR-0005 + ADR-0013 + ADR-0015)。
 * <p>新建时 fundCode/fundName/fundSubType/fundCategory/benchmarkIndexCode 由前端从字典搜索候选带入
 * (CONTEXT.md「基金字典搜索」);dcaAmount(每期定投金额)用户手填。fundCode/fundName 二选一即可,
 * 其余类型字段可缺省(尽力填+可覆盖,缺省时由后端兜底)。
 *
 * @param fundCode             基金代码(如 510300)
 * @param fundName             基金名称
 * @param fundCategory         基金类型(宽基/行业/主动/混合)
 * @param fundSubType          基金子类型(ETF/INDEX/INDEX_ENHANCED/ACTIVE)
 * @param benchmarkIndexCode   跟踪指数代码(如 000300.SH)
 * @param dcaAmount            每期定投金额(ADR-0015,每月最后交易日扣款;定投无上限、无计划期数)
 * @param initialMarketValue   入仓市值(可选):新建时录入已有持仓(当前市值口径),用 T-1 净值反算 shares;
 *                             null/非正数则走原 PENDING_HOLDING 流程(CONTEXT.md「初始持仓录入」)
 * @param costPerShare         成本单价(可选,仅 initialMarketValue 有值时生效):不填默认 T-1 净值;>0 校验;
 *                             存入 FundEntity.costPerShare 作为初始成本基准(ADR-0013)
 * @param openedAt             建仓时间(可选,仅 initialMarketValue 有值时生效):用户记得的大致建仓时点,
 *                             影响移动止盈的持仓期高点起算;null 则用 now。须 ≤ 今天
 */
public record FundCreateRequest(
        String fundCode,
        String fundName,
        FundCategory fundCategory,
        FundSubType fundSubType,
        String benchmarkIndexCode,
        BigDecimal dcaAmount,
        BigDecimal initialMarketValue,
        BigDecimal costPerShare,
        Instant openedAt) {

    /** 6 参数次构造:不录现有金额(走原 PENDING_HOLDING 流程)。维持现有调用方兼容。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal dcaAmount) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, dcaAmount, null, null, null);
    }

    /** 7 参数次构造:录入仓市值但不填建仓时间和成本单价(沿用现有调用方兼容)。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal dcaAmount, BigDecimal initialMarketValue) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, dcaAmount, initialMarketValue, null, null);
    }

    /** 8 参数次构造:录入仓市值+建仓时间但不填成本单价(兼容老调用方)。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal dcaAmount, BigDecimal initialMarketValue, Instant openedAt) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, dcaAmount, initialMarketValue, null, openedAt);
    }
}