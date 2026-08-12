package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
 * @param positionWarningEnabled 是否启用当前持仓占比提醒(可选,默认 true)
 * @param positionWarningRatio 当前持仓占比提醒线(可选,默认 30%,范围为 (0, 100%])
 * @param initialHoldingShares 持有份额(可选):新建时录入已有持仓;null 表示不录持仓；非正数为非法输入
 * @param costPerShare         成本单价(可选):新建持仓时不填默认 T-1 净值；更新时非 null 表示修正当前持仓成本；
 *                             两种场景都必须大于 0，且修正不回写历史交易或 FIFO lot(ADR-0013)
 * @param openedAt             建仓时间(可选,仅 initialHoldingShares 有值时生效):用户记得的大致建仓时点,
 *                             影响移动止盈的持仓期高点起算;null 则用 now。须 ≤ 今天
 */
public record FundCreateRequest(
        String fundCode,
        String fundName,
        FundCategory fundCategory,
        FundSubType fundSubType,
        String benchmarkIndexCode,
        Boolean positionWarningEnabled,
        BigDecimal positionWarningRatio,
        BigDecimal initialHoldingShares,
        BigDecimal costPerShare,
        Instant openedAt,
        List<String> groupNames) {

    /** 5 参数次构造:不录现有份额(走原 PENDING_HOLDING 流程)。维持现有调用方兼容。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, null, null, null, null, null, null);
    }

    /** 6 参数次构造:录入持有份额但不填建仓时间和成本单价(沿用现有调用方兼容)。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal initialHoldingShares) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, null, null, initialHoldingShares, null, null, null);
    }

    /** 7 参数次构造:录入持有份额+建仓时间但不填成本单价(兼容老调用方)。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal initialHoldingShares, Instant openedAt) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, null, null, initialHoldingShares, null, openedAt, null);
    }

    /** 8 参数次构造:兼容新增仓位提醒前的完整建仓调用方。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal initialHoldingShares, BigDecimal costPerShare, Instant openedAt) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode,
                null, null, initialHoldingShares, costPerShare, openedAt, null);
    }

    /** 兼容仓位提醒字段改名前的完整创建调用方。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal positionWarningRatio, BigDecimal initialHoldingShares,
                             BigDecimal costPerShare, Instant openedAt) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode,
                null, positionWarningRatio, initialHoldingShares, costPerShare, openedAt, null);
    }

    /** 兼容新增分组前的完整请求构造器。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             Boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                             BigDecimal initialHoldingShares, BigDecimal costPerShare, Instant openedAt) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, positionWarningEnabled,
                positionWarningRatio, initialHoldingShares, costPerShare, openedAt, null);
    }
}
