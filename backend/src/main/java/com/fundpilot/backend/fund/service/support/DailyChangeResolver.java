package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.EstimateStatus;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 三态今日涨跌判定纯函数(issue #38,PRD #34 / ADR-0008)。
 *
 * <p>「今日涨跌」是单一概念,值随时段切换数据源:
 * <ul>
 *   <li><b>估值前</b>(当日 gztime 尚未出现)= 0,isEstimated=false</li>
 *   <li><b>估值中/待公布</b>(当日 gztime 已出现且当日净值未落库)= fundgz gszzl,isEstimated=true;
 *       无 fundgz 估值时返回未知,不能用 T-1 vs T-2 冒充今日涨跌</li>
 *   <li><b>盘后</b>(当日净值已落库)= 当日累计净值 / 昨日累计净值 - 1,isEstimated=false</li>
 * </ul>
 *
 * <p>判定优先级:当日净值已落库→ 当日估值可用→ 当日估值尚未出现→ 空响应或失败。
 * 本逻辑仅消费普通非 QDII 基金的盘中估值；QDII 由调用方按确认净值发现日结算。
 *
 * <p>纯函数,无 Spring/DB 依赖,外部值(净值是否落库、落库净值、fundgz 估值及状态)由调用方注入。
 */
public final class DailyChangeResolver {

    private DailyChangeResolver() {
    }

    /**
     * @param todayNavConfirmed 当日净值是否已落库(盘后态判定)
     * @param latestNav         落库的最近一期累计净值(盘后态 / 降级态用)
     * @param previousNav       落库的上一期累计净值(同上)
     * @param estimate          fundgz 当日估值(估值阶段用,空则降级)
     * @param estimateStatus    最近一次估值刷新状态
     * @return 今日涨跌幅 + 是否估算
     */
    public static DailyChangeResult resolve(boolean todayNavConfirmed,
                                            BigDecimal latestNav, BigDecimal previousNav,
                                            Optional<FundEstimateSnapshot> estimate,
                                            EstimateStatus estimateStatus) {
        // 盘后态:当日净值已落库 → 用落库净值算(当日/昨日-1),非估算
        if (todayNavConfirmed) {
            return new DailyChangeResult(FundPnlCalculator.dailyChangePct(latestNav, previousNav), false);
        }
        // 当日估值存在 → 用 gszzl,标记估算
        if (estimate.isPresent() && estimate.get().estimatedChangePct() != null) {
            return new DailyChangeResult(estimate.get().estimatedChangePct(), true);
        }
        // 当日 gztime 尚未出现 → 今日涨跌为 0,持仓估值由调用方使用最近确认净值。
        if (estimateStatus == EstimateStatus.STALE || estimateStatus == EstimateStatus.NOT_ATTEMPTED) {
            return new DailyChangeResult(BigDecimal.ZERO, false);
        }
        // 空响应或失败 → 今日数据未知。T-1 vs T-2 是昨日涨跌,不能冒充今日值。
        return new DailyChangeResult(null, false);
    }
}
