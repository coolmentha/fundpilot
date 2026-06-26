package com.fundpilot.backend.market.service.support;

import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.enums.WeeklyMacdState;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 周线 MACD 计算器(CONTEXT.md「调节系数表」周 MACD 维度):
 * 把日累计净值序列按 ISO 周分组,每周取该周最后一个交易日的累计净值得到周序列,
 * 然后跑标准 12/26/9 EMA-MACD,根据最末两个周期的 MACD 柱状对比判定四态:
 * <ul>
 *   <li>柱 &gt; 0 → {@link WeeklyMacdState#RED_SHRINKING}(红柱期,本期统一按红柱缩小处理)</li>
 *   <li>柱 &lt; 0 且 |今| &gt; |上| → {@link WeeklyMacdState#GREEN_EXPANDING}</li>
 *   <li>柱 &lt; 0 且 |今| &lt;= |上| → {@link WeeklyMacdState#GREEN_SHRINKING}</li>
 * </ul>
 * <p>{@link WeeklyMacdState#DIVERGENCE_BOTTOM}(底背离)需双低点窗口识别,本期未实现,
 * 由后续优化补齐——v0.0.1 走不到此分支,系数表对应默认按 GREEN_SHRINKING 处理。
 * <p>不足 30 周返回 {@link Optional#empty()} 降级。
 */
public final class WeeklyMacdCalculator {

    private static final int FAST = 12;
    private static final int SLOW = 26;
    private static final int SIGNAL = 9;
    private static final int MIN_WEEKS = 30;

    private WeeklyMacdCalculator() {
    }

    public static Optional<WeeklyMacdState> calculate(List<FundNavSnapshot> dailyNav) {
        if (dailyNav == null || dailyNav.isEmpty()) {
            return Optional.empty();
        }
        List<BigDecimal> weekly = toWeeklyClose(dailyNav);
        if (weekly.size() < MIN_WEEKS) {
            return Optional.empty();
        }
        double[] price = weekly.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double[] emaFast = ema(price, FAST);
        double[] emaSlow = ema(price, SLOW);
        int n = price.length;
        double[] dif = new double[n];
        for (int i = 0; i < n; i++) {
            dif[i] = emaFast[i] - emaSlow[i];
        }
        double[] dea = ema(dif, SIGNAL);
        double[] bar = new double[n];
        for (int i = 0; i < n; i++) {
            bar[i] = 2.0 * (dif[i] - dea[i]);
        }
        double today = bar[n - 1];
        double prev = bar[n - 2];
        return Optional.of(classifyState(today, prev));
    }

    /**
     * 由最末两根 MACD 柱状值判定周 MACD 四态(package-private 便于单测直接覆盖判定分支)。
     * <ul>
     *   <li>今柱 &gt; 0 → {@link WeeklyMacdState#RED_SHRINKING}(红柱期统一按缩小处理)</li>
     *   <li>今柱 &lt; 0 且 |今| &gt; |上| → {@link WeeklyMacdState#GREEN_EXPANDING}</li>
     *   <li>今柱 &lt; 0 且 |今| &lt;= |上| → {@link WeeklyMacdState#GREEN_SHRINKING}</li>
     * </ul>
     * {@link WeeklyMacdState#DIVERGENCE_BOTTOM} 需双低点窗口识别,本期未实现。
     */
    static WeeklyMacdState classifyState(double todayBar, double prevBar) {
        if (todayBar > 0) {
            return WeeklyMacdState.RED_SHRINKING;
        }
        if (Math.abs(todayBar) > Math.abs(prevBar)) {
            return WeeklyMacdState.GREEN_EXPANDING;
        }
        return WeeklyMacdState.GREEN_SHRINKING;
    }

    /** 日序列按 ISO 周分组,每周取该周最后一个有数据的交易日的累计净值,按周末日期升序输出。 */
    private static List<BigDecimal> toWeeklyClose(List<FundNavSnapshot> daily) {
        Map<Instant, BigDecimal> byWeekEnd = new LinkedHashMap<>();
        for (FundNavSnapshot s : daily) {
            Instant weekEnd = s.navDate().atZone(ZoneOffset.UTC).toLocalDate()
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
            // 覆盖式累加——后到的同一周数据点会刷掉前者,保留最后一个交易日的累计净值
            byWeekEnd.put(weekEnd, s.accumulatedNav());
        }
        List<BigDecimal> result = new ArrayList<>(byWeekEnd.values());
        return result;
    }

    /** 标准 EMA:首点取 X[0] 作初值,EMA_t = α·X_t + (1-α)·EMA_{t-1},α = 2/(N+1)。 */
    private static double[] ema(double[] x, int n) {
        double[] result = new double[x.length];
        double alpha = 2.0 / (n + 1.0);
        result[0] = x[0];
        for (int i = 1; i < x.length; i++) {
            result[i] = alpha * x[i] + (1.0 - alpha) * result[i - 1];
        }
        return result;
    }
}
