package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 硬约束统一检查入口:五条加仓上限,返 {@code List<Breach>}(空=全部通过)。
 * <p>五条:① 建仓比例 ② 单只仓位 ③ 单类仓位 ④ 总权益仓位 ⑤ 单次加仓比例。
 * 上限读 {@link HardConstraintConfig}。MIN_HOLD_DAYS(5 交易日)不在本期检查——它依赖
 * {@link TradingCalendarService} 判定起算点,留给信号引擎 issue #12 在 evaluateSignal 里调。
 * <p>硬约束管"主动加仓不能突破上限";再平衡减仓(存量超限被动卖出)是另一套机制,不在此处。
 */
public final class HardConstraintChecker {

    private HardConstraintChecker() {
    }

    /**
     * @param category            基金类型(决定单只仓位上限:宽基/主动/混合 20%,行业 15%)
     * @param buildRatio          建仓比例(占 plannedTotalAmount)
     * @param singlePositionPct   单只基金当前占比
     * @param categoryPositionPct 该基金类型当前总占比
     * @param totalEquityPct      总权益仓位占比
     * @param singleAddRatio      本次加仓比例
     * @return 违反的约束列表,空表示全部通过
     */
    public static List<Breach> check5(FundCategory category,
                                      BigDecimal buildRatio,
                                      BigDecimal singlePositionPct,
                                      BigDecimal categoryPositionPct,
                                      BigDecimal totalEquityPct,
                                      BigDecimal singleAddRatio) {
        List<Breach> breaches = new ArrayList<>();
        check(breaches, "BUILD_RATIO", buildRatio, HardConstraintConfig.BUILD_RATIO);
        check(breaches, "SINGLE_POSITION_LIMIT", singlePositionPct, HardConstraintConfig.singlePositionLimit(category));
        check(breaches, "CATEGORY_POSITION_LIMIT", categoryPositionPct, HardConstraintConfig.CATEGORY_POSITION_LIMIT);
        check(breaches, "TOTAL_EQUITY_POSITION_LIMIT", totalEquityPct, HardConstraintConfig.TOTAL_EQUITY_POSITION_LIMIT);
        check(breaches, "SINGLE_ADD_RATIO_LIMIT", singleAddRatio, HardConstraintConfig.SINGLE_ADD_RATIO_LIMIT);
        return breaches;
    }

    private static void check(List<Breach> breaches, String name, BigDecimal actual, BigDecimal limit) {
        if (actual.compareTo(limit) > 0) {
            breaches.add(new Breach(name, actual, limit));
        }
    }
}
