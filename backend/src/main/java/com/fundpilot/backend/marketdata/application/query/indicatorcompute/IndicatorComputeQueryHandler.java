package com.fundpilot.backend.marketdata.application.query.indicatorcompute;

import com.fundpilot.backend.marketdata.application.gateway.indicatorcompute.IndicatorFundGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway.InvestmentTarget;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexBar;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexKlineRepository;
import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuation;
import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuationRepository;
import com.fundpilot.backend.marketdata.domain.indicatorcompute.Macd;
import com.fundpilot.backend.marketdata.domain.indicatorcompute.MovingAverage;
import com.fundpilot.backend.marketdata.domain.indicatorcompute.SeriesStatistics;
import com.fundpilot.backend.marketdata.domain.indicatorcompute.WeeklySeries;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指标按需计算：给定「基金 + 指标种类 + 窗口参数」现算指标取值序列。
 *
 * <p>数据全部来自已落库的净值历史、指数 K 线与指数估值，不做任何外部拉取；同一交易日内重复求值命中
 * 进程内缓存，每条提醒规则每天只算一次。
 */
@Service
@RequiredArgsConstructor
public class IndicatorComputeQueryHandler {

    /** 净值类指标回看的自然日数：覆盖 MA250 与「最新/前一取值」比较所需的 251 个交易日。 */
    private static final int NAV_LOOKBACK_DAYS = 1095;
    /** PE 历史分位固定窗口（交易日），本版本不做可调。 */
    private static final int PE_PERCENTILE_WINDOW = 1250;
    /** 周线 MACD 与既有快照一致的最小周数要求。 */
    private static final int WEEKLY_MACD_MIN_WEEKS = 30;
    private static final int MIN_VALUES = 1;
    private static final int MAX_VALUES = 5;
    private static final String PE_SOURCE = "CSINDEX_INDEX_CSI_DS_PE_PEG";

    static final String PRICE_VS_MA = "PRICE_VS_MA";
    static final String MA = "MA";
    static final String MA_CROSS = "MA_CROSS";
    static final String NAV_RANGE_POSITION = "NAV_RANGE_POSITION";
    static final String NAV_DRAWDOWN = "NAV_DRAWDOWN";
    static final String VOLUME_RATIO = "VOLUME_RATIO";
    static final String VOLUME_DROP = "VOLUME_DROP";
    static final String WEEKLY_MACD_HISTOGRAM = "WEEKLY_MACD_HISTOGRAM";
    static final String INDEX_PE = "INDEX_PE";
    static final String INDEX_PE_PERCENTILE = "INDEX_PE_PERCENTILE";

    private static final Set<String> SUPPORTED_CODES = Set.of(PRICE_VS_MA, MA, MA_CROSS, NAV_RANGE_POSITION,
            NAV_DRAWDOWN, VOLUME_RATIO, VOLUME_DROP, WEEKLY_MACD_HISTOGRAM, INDEX_PE, INDEX_PE_PERCENTILE);
    /** 日内缓存条目上限:试算参数组合不可枚举,超上限直接清空重算,防日内无界膨胀。 */
    private static final int MAX_CACHE_ENTRIES = 5_000;

    private final PublishedNavRepository navs;
    private final IndexKlineRepository klines;
    private final IndexValuationRepository valuations;
    private final IndicatorFundGateway funds;
    private final Clock clock;

    private final Map<String, List<IndicatorValue>> cache = new ConcurrentHashMap<>();
    private volatile LocalDate cacheDate;

    /** 全部可现算的指标种类，供跨模块契约核对使用。 */
    public static Set<String> supportedCodes() {
        return SUPPORTED_CODES;
    }

    @Transactional(readOnly = true)
    public List<IndicatorValue> recent(IndicatorComputeRequest request) {
        requireRequest(request);
        String code = request.indicatorCode().trim().toUpperCase(Locale.ROOT);
        Map<String, Integer> params = request.params() == null ? Map.of() : new TreeMap<>(request.params());
        IndicatorComputeRequest normalized = new IndicatorComputeRequest(request.fundProductId(), code, params,
                request.endExclusive(), request.count());
        evictWhenDateChanged();
        String key = normalized.fundProductId() + "|" + code + "|" + params + "|" + normalized.endExclusive()
                + "|" + normalized.count();
        List<IndicatorValue> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<IndicatorValue> computed = compute(normalized);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, computed);
        return computed;
    }

    public record IndicatorComputeRequest(long fundProductId, String indicatorCode, Map<String, Integer> params,
                                          Instant endExclusive, int count) {
    }

    public record IndicatorValue(Instant asOf, BigDecimal value) {
    }

    private void evictWhenDateChanged() {
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        if (!today.equals(cacheDate)) {
            cache.clear();
            cacheDate = today;
        }
    }

    private List<IndicatorValue> compute(IndicatorComputeRequest request) {
        IndicatorFundGateway.IndicatorFund fund = funds.findById(request.fundProductId()).orElse(null);
        if (fund == null) {
            return List.of();
        }
        return switch (request.indicatorCode()) {
            case PRICE_VS_MA -> priceVsMa(fund, request);
            case MA -> movingAverage(fund, request);
            case MA_CROSS -> maCross(fund, request);
            case NAV_RANGE_POSITION -> navRangePosition(fund, request);
            case NAV_DRAWDOWN -> navDrawdown(fund, request);
            case WEEKLY_MACD_HISTOGRAM -> weeklyMacdHistogram(fund, request);
            case VOLUME_RATIO -> volumeRatio(fund, request, false);
            case VOLUME_DROP -> volumeRatio(fund, request, true);
            case INDEX_PE -> indexPe(fund, request);
            case INDEX_PE_PERCENTILE -> indexPePercentile(fund, request);
            default -> throw new IllegalArgumentException("不支持的指标种类: " + request.indicatorCode());
        };
    }

    /** 累计净值相对均线的偏离率：正数表示在均线上方。 */
    private List<IndicatorValue> priceVsMa(IndicatorFundGateway.IndicatorFund fund,
                                           IndicatorComputeRequest request) {
        NavSeries series = loadNavs(fund, request);
        if (series.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> average = MovingAverage.simple(series.values(), param(request, "window", 250));
        List<BigDecimal> deviation = new ArrayList<>(series.size());
        for (int index = 0; index < series.size(); index++) {
            BigDecimal mean = average.get(index);
            deviation.add(mean == null || mean.signum() == 0 ? null
                    : series.values().get(index).subtract(mean).divide(mean, MathContext.DECIMAL64));
        }
        return tail(series.dates(), deviation, request.count());
    }

    private List<IndicatorValue> movingAverage(IndicatorFundGateway.IndicatorFund fund,
                                               IndicatorComputeRequest request) {
        NavSeries series = loadNavs(fund, request);
        return tail(series.dates(), MovingAverage.simple(series.values(), param(request, "window", 250)),
                request.count());
    }

    /** 快线均线减慢线均线：大于零为多头排列，由负转正即金叉。 */
    private List<IndicatorValue> maCross(IndicatorFundGateway.IndicatorFund fund,
                                         IndicatorComputeRequest request) {
        int fast = param(request, "fast", 20);
        int slow = param(request, "slow", 50);
        if (fast >= slow) {
            throw new IllegalArgumentException("均线快线窗口必须小于慢线窗口");
        }
        NavSeries series = loadNavs(fund, request);
        if (series.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> fastAverage = MovingAverage.simple(series.values(), fast);
        List<BigDecimal> slowAverage = MovingAverage.simple(series.values(), slow);
        List<BigDecimal> spread = new ArrayList<>(series.size());
        for (int index = 0; index < series.size(); index++) {
            BigDecimal quick = fastAverage.get(index);
            BigDecimal slowValue = slowAverage.get(index);
            spread.add(quick == null || slowValue == null ? null : quick.subtract(slowValue));
        }
        return tail(series.dates(), spread, request.count());
    }

    /** 累计净值在近 window 个交易日区间中的位置：0 为区间最低，1 为区间最高。 */
    private List<IndicatorValue> navRangePosition(IndicatorFundGateway.IndicatorFund fund,
                                                  IndicatorComputeRequest request) {
        NavSeries series = loadNavs(fund, request);
        if (series.isEmpty()) {
            return List.of();
        }
        int window = param(request, "window", 250);
        List<BigDecimal> minimum = SeriesStatistics.trailingMinimum(series.values(), window);
        List<BigDecimal> maximum = SeriesStatistics.trailingMaximum(series.values(), window);
        List<BigDecimal> positions = new ArrayList<>(series.size());
        for (int index = 0; index < series.size(); index++) {
            BigDecimal low = minimum.get(index);
            BigDecimal high = maximum.get(index);
            BigDecimal range = low == null || high == null ? null : high.subtract(low);
            positions.add(range == null || range.signum() == 0 ? null
                    : series.values().get(index).subtract(low).divide(range, MathContext.DECIMAL64));
        }
        return tail(series.dates(), positions, request.count());
    }

    /** 累计净值距近 window 个交易日峰值的回撤比例。 */
    private List<IndicatorValue> navDrawdown(IndicatorFundGateway.IndicatorFund fund,
                                             IndicatorComputeRequest request) {
        NavSeries series = loadNavs(fund, request);
        if (series.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> peak = SeriesStatistics.trailingMaximum(series.values(), param(request, "window", 250));
        List<BigDecimal> drawdowns = new ArrayList<>(series.size());
        for (int index = 0; index < series.size(); index++) {
            BigDecimal high = peak.get(index);
            drawdowns.add(high == null || high.signum() == 0 ? null
                    : high.subtract(series.values().get(index)).divide(high, MathContext.DECIMAL64));
        }
        return tail(series.dates(), drawdowns, request.count());
    }

    /** 周线 MACD 柱高：正为红柱、负为绿柱，柱高由负转正即金叉。 */
    private List<IndicatorValue> weeklyMacdHistogram(IndicatorFundGateway.IndicatorFund fund,
                                                     IndicatorComputeRequest request) {
        NavSeries series = loadNavs(fund, request);
        if (series.isEmpty()) {
            return List.of();
        }
        int fast = param(request, "fast", 12);
        int slow = param(request, "slow", 26);
        int signal = param(request, "signal", 9);
        List<WeeklySeries.WeekPoint> weekly = WeeklySeries.lastValuePerWeek(series.dates(), series.values());
        if (weekly.size() < Math.max(WEEKLY_MACD_MIN_WEEKS, slow + signal)) {
            return List.of();
        }
        List<Instant> weekEnds = weekly.stream().map(WeeklySeries.WeekPoint::weekEnd).toList();
        List<BigDecimal> weeklyValues = weekly.stream().map(WeeklySeries.WeekPoint::value).toList();
        Macd.Line line = Macd.compute(weeklyValues, fast, slow, signal);
        List<BigDecimal> histogram = new ArrayList<>(weekEnds.size());
        for (double value : line.histogram()) {
            histogram.add(BigDecimal.valueOf(value));
        }
        return tail(weekEnds, histogram, request.count());
    }

    /**
     * 基准指数量能比：最新完整日 K 成交量与近 window 日均量之比。
     *
     * @param dropOnly 为真时只统计放量下跌（收阴且放量），收阳当日取 0，与旧量能状态 HIGH_DROP 同口径
     */
    private List<IndicatorValue> volumeRatio(IndicatorFundGateway.IndicatorFund fund,
                                             IndicatorComputeRequest request, boolean dropOnly) {
        String indexCode = fund.benchmarkIndexCode();
        if (indexCode == null || indexCode.isBlank()) {
            return List.of();
        }
        List<IndexBar> bars = klines.findAll(indexCode.trim()).stream()
                .filter(IndexBar::isComplete)
                .filter(bar -> bar.volume() != null)
                .filter(bar -> bar.tradeDate().isBefore(request.endExclusive()))
                .toList();
        if (bars.isEmpty()) {
            return List.of();
        }
        List<Instant> dates = bars.stream().map(IndexBar::tradeDate).toList();
        List<BigDecimal> volumes = bars.stream().map(bar -> BigDecimal.valueOf(bar.volume())).toList();
        List<BigDecimal> average = SeriesStatistics.trailingAverage(volumes, param(request, "window", 20));
        List<BigDecimal> ratios = new ArrayList<>(volumes.size());
        for (int index = 0; index < volumes.size(); index++) {
            BigDecimal mean = average.get(index);
            if (mean == null || mean.signum() == 0) {
                ratios.add(null);
                continue;
            }
            ratios.add(dropOnly && !bearish(bars.get(index))
                    ? BigDecimal.ZERO
                    : volumes.get(index).divide(mean, MathContext.DECIMAL64));
        }
        return tail(dates, ratios, request.count());
    }

    /** 当日是否收阴；开收盘缺一即视为未下跌，放量下跌口径下取 0。 */
    private static boolean bearish(IndexBar bar) {
        return bar.open() != null && bar.close() != null && bar.close().compareTo(bar.open()) < 0;
    }

    /** 基准指数最新市盈率。 */
    private List<IndicatorValue> indexPe(IndicatorFundGateway.IndicatorFund fund,
                                         IndicatorComputeRequest request) {
        List<IndexValuation> history = loadValuations(fund, request);
        return tail(history.stream().map(IndexValuation::tradeDate).toList(),
                history.stream().map(IndexValuation::peRatio).toList(), request.count());
    }

    /** 最新市盈率在固定窗口历史样本中的分位：0 为历史最低、1 为历史最高。 */
    private List<IndicatorValue> indexPePercentile(IndicatorFundGateway.IndicatorFund fund,
                                                   IndicatorComputeRequest request) {
        List<IndexValuation> history = loadValuations(fund, request);
        List<IndicatorValue> result = new ArrayList<>(request.count());
        for (int end = history.size() - 1; end >= 0 && result.size() < request.count(); end--) {
            BigDecimal current = history.get(end).peRatio();
            if (current == null) {
                continue;
            }
            int start = Math.max(0, end + 1 - PE_PERCENTILE_WINDOW);
            List<BigDecimal> samples = history.subList(start, end + 1).stream()
                    .map(IndexValuation::peRatio).filter(sample -> sample != null).toList();
            if (samples.isEmpty()) {
                continue;
            }
            result.add(new IndicatorValue(history.get(end).tradeDate(),
                    SeriesStatistics.percentileRank(samples, current)));
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    private List<IndexValuation> loadValuations(IndicatorFundGateway.IndicatorFund fund,
                                                IndicatorComputeRequest request) {
        String indexCode = fund.benchmarkIndexCode();
        if (indexCode == null || indexCode.isBlank()) {
            return List.of();
        }
        return valuations.findHistory(indexCode.trim(), PE_SOURCE, request.endExclusive());
    }

    private NavSeries loadNavs(IndicatorFundGateway.IndicatorFund fund, IndicatorComputeRequest request) {
        if (!supportsStandardNav(fund)) {
            return NavSeries.empty();
        }
        Instant start = request.endExclusive().minus(NAV_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Instant> dates = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (PublishedNav nav : navs.findByProductIdAndDateRange(fund.fundProductId(), start,
                request.endExclusive())) {
            if (nav.accumulatedNav() == null) {
                continue;
            }
            dates.add(nav.navDate());
            values.add(nav.accumulatedNav());
        }
        return new NavSeries(dates, values);
    }

    /** 货币与 REIT 的累计净值口径不适用均线类指标。 */
    private static boolean supportsStandardNav(IndicatorFundGateway.IndicatorFund fund) {
        return fund.investmentTarget() != InvestmentTarget.MONEY_MARKET
                && fund.investmentTarget() != InvestmentTarget.REIT;
    }

    /** 取序列末尾 count 个非空取值，按日期升序返回（最新在最后）。 */
    private static List<IndicatorValue> tail(List<Instant> dates, List<BigDecimal> values, int count) {
        List<IndicatorValue> result = new ArrayList<>(count);
        for (int index = values.size() - 1; index >= 0 && result.size() < count; index--) {
            if (values.get(index) != null) {
                result.add(new IndicatorValue(dates.get(index), values.get(index)));
            }
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    private static int param(IndicatorComputeRequest request, String name, int defaultValue) {
        Integer value = request.params() == null ? null : request.params().get(name);
        return value == null ? defaultValue : value;
    }

    private static void requireRequest(IndicatorComputeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("指标计算请求不能为空");
        }
        if (request.fundProductId() <= 0) {
            throw new IllegalArgumentException("基金产品标识必须为正数");
        }
        if (request.indicatorCode() == null || request.indicatorCode().isBlank()) {
            throw new IllegalArgumentException("指标种类不能为空");
        }
        if (request.endExclusive() == null) {
            throw new IllegalArgumentException("数据截止时间不能为空");
        }
        if (request.count() < MIN_VALUES || request.count() > MAX_VALUES) {
            throw new IllegalArgumentException("取值个数必须在 " + MIN_VALUES + "~" + MAX_VALUES + " 之间");
        }
        if (!SUPPORTED_CODES.contains(request.indicatorCode().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不支持的指标种类: " + request.indicatorCode());
        }
    }

    private record NavSeries(List<Instant> dates, List<BigDecimal> values) {

        static NavSeries empty() {
            return new NavSeries(List.of(), List.of());
        }

        int size() {
            return values.size();
        }

        boolean isEmpty() {
            return values.isEmpty();
        }
    }
}