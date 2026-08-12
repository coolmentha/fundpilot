package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indicator.MarketIndicatorCommandHandler;
import com.fundpilot.backend.marketdata.application.command.navpublishing.NavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.event.indicatorrefresh.MarketIndicatorsRefreshed;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.MarketIndicatorRefreshEventGateway;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexKlineSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexValuationSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndicatorRefreshCommandHandler {
    private static final int TOTAL_BATCHES = 3;
    private static final String FULL_KLINE_LIMIT = "400";
    private static final String INCREMENTAL_KLINE_LIMIT = "10";
    private static final int YEAR_LINE_WINDOW = 250;
    private static final int SIXTY_DAY_WINDOW = 60;
    private static final int WEEKLY_DROP_WINDOW = 5;
    private static final int VOLUME_WINDOW = 20;

    private final TrackedNavProductGateway products;
    private final PublishedNavSourceGateway navSource;
    private final PublishedIndexKlineSourceGateway klineSource;
    private final MarketIndicatorRefreshEventGateway events;
    private final NavPublishingCommandHandler navPublisher;
    private final IndexKlineCommandHandler klineCommands;
    private final IndexKlineQueryHandler klineQueries;
    private final MarketIndicatorCommandHandler indicators;
    private final Clock clock;
    private final PublishedIndexValuationSourceGateway valuationSource;
    private final IndexValuationCommandHandler valuationCommands;
    private final IndexValuationQueryHandler valuationQueries;

    public void refreshBatch(int batchNumber) {
        refresh(products.findAll().stream().filter(product -> product.legacyFundId() != null
                && Math.floorMod(product.legacyFundId().hashCode(), TOTAL_BATCHES) == batchNumber).toList());
    }

    public void refreshAll() {
        refresh(products.findAll());
    }

    public void refreshOne(long legacyFundId) {
        products.findByLegacyFundId(legacyFundId).ifPresent(product ->
                refreshOne(product, new HashMap<>(), new HashSet<>(), new HashSet<>()));
    }

    public void refreshOneForPortfolioFund(long portfolioFundId) {
        products.findByPortfolioFundId(portfolioFundId).ifPresent(product ->
                refreshOne(product, new HashMap<>(), new HashSet<>(), new HashSet<>()));
    }

    public void refreshBatchAndPublishCompletion(int batchNumber) {
        refreshBatch(batchNumber);
        events.publishMarketIndicatorsRefreshed(new MarketIndicatorsRefreshed(clock.instant()));
    }

    public void refreshOne(RefreshTarget target) {
        refreshOne(new TrackedNavProductGateway.TrackedProduct(target.legacyFundId(), target.fundProductId(),
                target.fundCode(), target.fundName(), target.benchmarkIndexCode(), target.investmentTarget()),
                new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    private void refresh(List<TrackedNavProductGateway.TrackedProduct> targets) {
        Map<String, PublishedIndexKlineSourceGateway.IndexKline> klineCache = new HashMap<>();
        Set<String> persistedKlines = new HashSet<>();
        Set<String> refreshedValuations = new HashSet<>();
        int success = 0;
        int failure = 0;
        for (var target : targets) {
            try {
                if (supportsStandardNav(target)) {
                    refreshOne(target, klineCache, persistedKlines, refreshedValuations);
                    success++;
                }
            } catch (RuntimeException ex) {
                failure++;
                log.warn("拉取产品 {} 行情指标失败，跳过当日 snapshot", target.fundProductId(), ex);
            }
        }
        log.info("行情指标刷新完成: 成功 {} 只，失败 {} 只", success, failure);
    }

    private void refreshOne(TrackedNavProductGateway.TrackedProduct target,
                            Map<String, PublishedIndexKlineSourceGateway.IndexKline> klineCache,
                            Set<String> persistedKlines, Set<String> refreshedValuations) {
        if (!supportsStandardNav(target)) return;
        List<PublishedNavSourceGateway.NavSnapshot> history = navSource.fetchHistory(target.fundCode());
        if (history == null || history.isEmpty()) {
            throw new IllegalStateException("fund_code=" + target.fundCode() + " 净值历史为空");
        }
        List<PublishedNavSourceGateway.NavSnapshot> ordered = history.stream()
                .sorted(Comparator.comparing(PublishedNavSourceGateway.NavSnapshot::navDate)).toList();
        List<BigDecimal> accumulated = ordered.stream()
                .map(PublishedNavSourceGateway.NavSnapshot::accumulatedNav).toList();
        var latest = ordered.getLast();
        Optional<YearLine> yearLine = yearLine(accumulated);
        Optional<String> macd = weeklyMacd(ordered);
        Optional<Boolean> sixtyDayHigh = sixtyDayHigh(accumulated);
        Optional<BigDecimal> weeklyDrop = weeklyDrop(accumulated);
        Optional<String> volumeState = Optional.empty();
        String indexCode = target.benchmarkIndexCode();
        PublishedIndexKlineSourceGateway.IndexKline kline = null;
        if (indexCode != null && !indexCode.isBlank()) {
            try {
                String limit = klineQueries.exists(indexCode) ? INCREMENTAL_KLINE_LIMIT : FULL_KLINE_LIMIT;
                kline = klineCache.computeIfAbsent(indexCode,
                        key -> klineSource.fetch(toSecid(key), limit));
            } catch (RuntimeException ex) {
                log.warn("产品 {} 指数 K 线拉取失败，volumeState 留空", target.fundProductId(), ex);
            }
        }
        navPublisher.publishNewer(target.legacyFundId(), target.fundProductId(), target.fundCode(), ordered.stream()
                .map(nav -> new NavPublishingCommandHandler.NavCandidate(nav.navDate(), nav.unitNav(), nav.accumulatedNav()))
                .toList());
        if (kline != null && persistedKlines.add(indexCode) && !kline.bars().isEmpty()) {
            klineCommands.upsert(indexCode, kline.bars().stream().map(bar ->
                    new IndexKlineCommandHandler.Bar(bar.tradeDate(), bar.open(), bar.high(), bar.low(),
                            bar.close(), bar.volume())).toList());
        }
        refreshValuation(indexCode, refreshedValuations);
        volumeState = volumeStateFromStored(indexCode);
        Instant today = ChinaTradingDate.toUtcDate(clock.instant());
        indicators.upsert(target.legacyFundId(), target.fundProductId(), target.fundCode(), today,
                latest.accumulatedNav(), yearLine.map(YearLine::above).orElse(null),
                yearLine.map(YearLine::rising).orElse(false), macd.orElse(null), volumeState.orElse(null),
                weeklyDrop.orElse(null), sixtyDayHigh.orElse(false));
    }

    /** 估值请求在任何写事务外完成；失败只影响低估策略，不影响既有行情指标刷新。 */
    private void refreshValuation(String indexCode, Set<String> refreshedValuations) {
        if (indexCode == null || indexCode.isBlank()) return;
        if (!refreshedValuations.add(indexCode)) return;
        String source = "CSINDEX_INDEX_CSI_DS_PE_PEG";
        LocalDate end = clock.instant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate start = valuationQueries.latest(indexCode, source)
                .map(value -> value.tradeDate().atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1))
                .orElse(LocalDate.of(2000, 1, 1));
        if (start.isAfter(end)) return;
        try {
            var values = valuationSource.fetch(indexCode,
                    start.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                    end.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
            if (!values.isEmpty()) {
                valuationCommands.upsert(values.stream().map(value -> new IndexValuationCommandHandler.Input(indexCode,
                        value.tradeDate(), value.peRatio(), source)).toList());
            }
        } catch (RuntimeException ex) {
            log.warn("指数 {} PE 历史刷新失败，低估策略本期可能跳过", indexCode, ex);
        }
    }

    private static boolean supportsStandardNav(TrackedNavProductGateway.TrackedProduct product) {
        if (product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.MONEY_MARKET
                || product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.REIT) return false;
        String name = product.fundName();
        return name == null || (!name.contains("货币") && !name.toUpperCase(Locale.ROOT).contains("REIT")
                && !name.contains("不动产投资信托"));
    }

    private static String toSecid(String indexCode) {
        int dot = indexCode.indexOf('.');
        if (dot <= 0 || dot >= indexCode.length() - 1) return indexCode;
        String prefix = switch (indexCode.substring(dot + 1).toUpperCase(Locale.ROOT)) {
            case "SH" -> "1.";
            case "SZ" -> "0.";
            case "CSI" -> "2.";
            default -> null;
        };
        return prefix == null ? indexCode : prefix + indexCode.substring(0, dot);
    }

    private static Optional<YearLine> yearLine(List<BigDecimal> values) {
        if (values.size() < YEAR_LINE_WINDOW + 1) return Optional.empty();
        int size = values.size();
        BigDecimal today = BigDecimal.ZERO;
        BigDecimal yesterday = BigDecimal.ZERO;
        for (int index = size - YEAR_LINE_WINDOW; index < size; index++) today = today.add(values.get(index));
        for (int index = size - YEAR_LINE_WINDOW - 1; index < size - 1; index++) yesterday = yesterday.add(values.get(index));
        BigDecimal divisor = BigDecimal.valueOf(YEAR_LINE_WINDOW);
        BigDecimal todayAverage = today.divide(divisor, MathContext.DECIMAL64);
        BigDecimal yesterdayAverage = yesterday.divide(divisor, MathContext.DECIMAL64);
        return Optional.of(new YearLine(values.getLast().compareTo(todayAverage) > 0,
                todayAverage.compareTo(yesterdayAverage) > 0));
    }

    private static Optional<Boolean> sixtyDayHigh(List<BigDecimal> values) {
        if (values.size() < SIXTY_DAY_WINDOW) return Optional.empty();
        return Optional.of(values.getLast().compareTo(values.subList(values.size() - SIXTY_DAY_WINDOW, values.size())
                .stream().max(BigDecimal::compareTo).orElseThrow()) >= 0);
    }

    private static Optional<BigDecimal> weeklyDrop(List<BigDecimal> values) {
        if (values.size() < WEEKLY_DROP_WINDOW) return Optional.empty();
        BigDecimal start = values.get(values.size() - WEEKLY_DROP_WINDOW);
        return Optional.of(start.subtract(values.getLast()).divide(start, MathContext.DECIMAL64));
    }

    private static Optional<String> weeklyMacd(List<PublishedNavSourceGateway.NavSnapshot> daily) {
        Map<Instant, BigDecimal> weekly = new HashMap<>();
        for (var nav : daily) {
            Instant weekEnd = nav.navDate().atZone(ZoneOffset.UTC).toLocalDate()
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atStartOfDay(ZoneOffset.UTC).toInstant();
            weekly.put(weekEnd, nav.accumulatedNav());
        }
        List<BigDecimal> values = weekly.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList();
        if (values.size() < 30) return Optional.empty();
        double[] prices = values.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double[] fast = ema(prices, 12);
        double[] slow = ema(prices, 26);
        double[] dif = new double[prices.length];
        for (int index = 0; index < prices.length; index++) dif[index] = fast[index] - slow[index];
        double[] dea = ema(dif, 9);
        double current = 2 * (dif[dif.length - 1] - dea[dea.length - 1]);
        double previous = 2 * (dif[dif.length - 2] - dea[dea.length - 2]);
        return Optional.of(current > 0 ? current > previous ? "RED_EXPANDING" : "RED_SHRINKING"
                : Math.abs(current) > Math.abs(previous) ? "GREEN_EXPANDING" : "GREEN_SHRINKING");
    }

    private static double[] ema(double[] values, int period) {
        double[] result = new double[values.length];
        result[0] = values[0];
        double alpha = 2.0 / (period + 1.0);
        for (int index = 1; index < values.length; index++) result[index] = alpha * values[index]
                + (1 - alpha) * result[index - 1];
        return result;
    }

    /** 基于已落库完整 K 线序列计算成交量状态，避免增量刷新 10 根窗口不足被覆盖为 null。 */
    private Optional<String> volumeStateFromStored(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) return Optional.empty();
        try {
            List<IndexKlineQueryHandler.Bar> stored = klineQueries.findAll(indexCode);
            return volumeState(stored.stream().map(bar -> new BarInput(bar.open(), bar.close(),
                    bar.volume())).toList());
        } catch (RuntimeException ex) {
            log.warn("指数 {} 本地 K 线读取失败，volumeState 留空", indexCode, ex);
            return Optional.empty();
        }
    }

    private static Optional<String> volumeState(List<BarInput> bars) {
        if (bars.size() < VOLUME_WINDOW) return Optional.empty();
        double average = bars.subList(bars.size() - VOLUME_WINDOW, bars.size()).stream()
                .mapToLong(BarInput::volume).average().orElseThrow();
        var latest = bars.getLast();
        if (latest.volume() >= average * 1.5 && latest.close().compareTo(latest.open()) < 0) {
            return Optional.of("HIGH_DROP");
        }
        return Optional.of(latest.volume() < average * 0.5 ? "LOW_STABLE" : "NORMAL");
    }

    private record BarInput(java.math.BigDecimal open, java.math.BigDecimal close, long volume) {}

    public record RefreshTarget(Long legacyFundId, long fundProductId, String fundCode, String fundName,
                                String benchmarkIndexCode,
                                TrackedNavProductGateway.InvestmentTarget investmentTarget) {}
    private record YearLine(boolean above, boolean rising) {}
}
