package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 基金新建/更新请求 DTO(issue #16 + ADR-0005)。
 * <p>新建时 fundCode/fundName/fundSubType/fundCategory/benchmarkIndexCode 由前端从字典搜索候选带入
 * (CONTEXT.md「基金字典搜索」);plannedTotalAmount 用户手填。fundCode/fundName 二选一即可,
 * 其余类型字段可缺省(尽力填+可覆盖,缺省时由后端兜底)。
 *
 * @param fundCode             基金代码(如 510300)
 * @param fundName             基金名称
 * @param fundCategory         基金类型(宽基/行业/主动/混合)
 * @param fundSubType          基金子类型(ETF/INDEX/INDEX_ENHANCED/ACTIVE)
 * @param benchmarkIndexCode   跟踪指数代码(如 000300.SH)
 * @param plannedTotalAmount   计划总仓位金额
 * @param existingAmount       现有金额(可选):新建时录入已有持仓(当前市值口径),用最近一期净值同步确认成建仓交易;
 *                             null/非正数则走原 PENDING_HOLDING 流程(CONTEXT.md「初始持仓录入」)
 * @param openedAt             建仓时间(可选,仅 existingAmount 有值时生效):用户记得的大致建仓时点,
 *                             影响移动止盈的持仓期高点起算;null 则用 now。须 ≤ 今天
 */
public record FundCreateRequest(
        String fundCode,
        String fundName,
        FundCategory fundCategory,
        FundSubType fundSubType,
        String benchmarkIndexCode,
        BigDecimal plannedTotalAmount,
        BigDecimal existingAmount,
        Instant openedAt) {

    /** 6 参数次构造:不录现有金额(走原 PENDING_HOLDING 流程)。维持现有调用方兼容。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal plannedTotalAmount) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, plannedTotalAmount, null, null);
    }

    /** 7 参数次构造:录现有金额但不填建仓时间(openedAt 用 now)。维持 existingAmount 调用方兼容。 */
    public FundCreateRequest(String fundCode, String fundName, FundCategory fundCategory,
                             FundSubType fundSubType, String benchmarkIndexCode,
                             BigDecimal plannedTotalAmount, BigDecimal existingAmount) {
        this(fundCode, fundName, fundCategory, fundSubType, benchmarkIndexCode, plannedTotalAmount, existingAmount, null);
    }
}
