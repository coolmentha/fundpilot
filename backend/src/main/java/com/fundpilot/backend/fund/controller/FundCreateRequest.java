package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 基金新建/更新请求 DTO(issue #16 + ADR-0005 + ADR-0013)。
 * <p>新建时 fundCode/fundName/fundSubType/fundCategory/benchmarkIndexCode 由前端从字典搜索候选带入
 * (CONTEXT.md「基金字典搜索」)。fundCode/fundName 二选一即可,
 * 其余类型字段可缺省(尽力填+可覆盖,缺省时由后端兜底)。
 *
 * <p>金字塔加仓机制移除后不再有 plannedTotalAmount 字段——买入完全由用户手动/定投决定。
 *
 * @param fundCode             基金代码(如 510300)
 * @param fundName             基金名称
 * @param fundCategory         基金类型(宽基/行业/主动/混合)
 * @param fundSubType          基金子类型(ETF/INDEX/INDEX_ENHANCED/ACTIVE)
 * @param benchmarkIndexCode   跟踪指数代码(如 000300.SH)
 * @param maxPositionRatio     单基金仓位上限比例(可选,默认 30%,只能在 (0, 30%] 内调整)
 * @param initialMarketValue   入仓市值(可选):新建时录入已有持仓(当前市值口径),用 T-1 净值反算 shares;
 *                             null 表示不录持仓；非正数为非法输入
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
        BigDecimal maxPositionRatio,
        BigDecimal initialMarketValue,
        BigDecimal costPerShare,
        Instant openedAt) {

    /** 5 参数次构造:不录现有金额(走原 PENDING_HOLDING 流程)。维持现有调用方兼容。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, null, null, null, null);
    }

    /** 6 参数次构造:录入仓市值但不填建仓时间和成本单价(沿用现有调用方兼容)。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal initialMarketValue) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, null, initialMarketValue, null, null);
    }

    /** 7 参数次构造:录入仓市值+建仓时间但不填成本单价(兼容老调用方)。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal initialMarketValue, Instant openedAt) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, null, initialMarketValue, null, openedAt);
    }

    /** 8 参数次构造:兼容新增仓位上限前的完整建仓调用方。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal initialMarketValue, BigDecimal costPerShare, Instant openedAt) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode,
                null, initialMarketValue, costPerShare, openedAt);
    }
}
