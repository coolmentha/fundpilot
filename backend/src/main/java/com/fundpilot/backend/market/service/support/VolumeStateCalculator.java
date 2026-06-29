package com.fundpilot.backend.market.service.support;

import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.enums.VolumeState;

import java.util.List;
import java.util.Optional;

/**
 * 成交量状态计算器(CONTEXT.md「调节系数表」量能维度):
 * 基于指数 K 线最近 20 根的成交量均值,按 issue #7 给定的 1.5× 阈值判定放量,
 * 并补充 0.5× 阈值识别地量(issue 原文仅给 1.5×,LOW_STABLE 的 0.5× 为本期补充推断)。
 * <ul>
 *   <li>最新量 ≥ 1.5× 均量 且 当日下跌(close &lt; open) → {@link VolumeState#HIGH_DROP}</li>
 *   <li>最新量 &lt; 0.5× 均量 → {@link VolumeState#LOW_STABLE}(地量企稳)</li>
 *   <li>其余 → {@link VolumeState#NORMAL}</li>
 * </ul>
 * <p>注意:放量但上涨不算 HIGH_DROP(字面「放量下跌」要求同时满足)。
 * 不足 20 根返回 {@link Optional#empty()} 降级。
 */
public final class VolumeStateCalculator {

    private static final int WINDOW = 20;
    private static final double HIGH_THRESHOLD = 1.5;
    private static final double LOW_THRESHOLD = 0.5;

    private VolumeStateCalculator() {
    }

    public static Optional<VolumeState> calculate(IndexKline kline) {
        if (kline == null || kline.bars() == null || kline.bars().size() < WINDOW) {
            return Optional.empty();
        }
        List<IndexKline.Bar> bars = kline.bars();
        int n = bars.size();
        long sum = 0L;
        for (int i = n - WINDOW; i < n; i++) {
            sum += bars.get(i).volume();
        }
        double mean = (double) sum / WINDOW;
        IndexKline.Bar last = bars.get(n - 1);
        double latestVolume = last.volume();
        boolean dropping = last.close().compareTo(last.open()) < 0;

        if (latestVolume >= mean * HIGH_THRESHOLD && dropping) {
            return Optional.of(VolumeState.HIGH_DROP);
        }
        if (latestVolume < mean * LOW_THRESHOLD) {
            return Optional.of(VolumeState.LOW_STABLE);
        }
        return Optional.of(VolumeState.NORMAL);
    }
}
