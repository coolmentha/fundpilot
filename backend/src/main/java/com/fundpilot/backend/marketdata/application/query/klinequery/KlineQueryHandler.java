package com.fundpilot.backend.marketdata.application.query.klinequery;

import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.gateway.klinequery.IndexKlineSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.application.query.navhistory.NavHistoryQueryHandler;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KlineQueryHandler {
    private static final String HISTORY_START = "1900-01-01T00:00:00Z";
    private static final String HISTORY_END = "2100-01-01T00:00:00Z";
    /** 东财单次拉取根数上限：1200 根约 5 年日线，保证聚合月K ≥34 根可算 MACD(12,26,9)。 */
    private static final String KLINE_LIMIT = "1200";
    /** 月K凑够 34 根（MACD 首个非空值门槛）约需 740 个交易日（34 个月 × 约 21 交易日 + 节假日余量）。 */
    private static final int MIN_DAILY_BARS = 740;
    /** 历史回补重试间隔：源本身历史不足时避免每次查询都打远程。 */
    private static final Duration BACKFILL_RETRY_INTERVAL = Duration.ofHours(24);

    private final OwnedFundProductGateway products;
    private final IndexKlineQueryHandler cachedKlines;
    private final IndexKlineSourceGateway source;
    private final NavHistoryQueryHandler navs;
    private final IndexKlineCommandHandler klineCommands;
    private final Map<String, Instant> backfillAttemptedAt = new ConcurrentHashMap<>();

    public Kline getKline(long legacyFundId, String period) {
        OwnedFundProductGateway.Product product = products.findOwned(legacyFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "基金不存在: " + legacyFundId));
        return getKline(product, period, legacyFundId);
    }

    public Kline getKlineForPortfolioFund(long portfolioFundId, String period) {
        OwnedFundProductGateway.Product product = products.findOwnedByPortfolioFundId(portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "基金不存在: " + portfolioFundId));
        return getKline(product, period, portfolioFundId);
    }

    private Kline getKline(OwnedFundProductGateway.Product product, String period, long fundId) {
        String benchmark = product.benchmarkIndexCode();
        if (isIndexLike(product.productType()) && benchmark != null && !benchmark.isBlank()) {
            List<IndexKlineQueryHandler.Bar> cached = cachedKlines.findAll(benchmark);
            if (!cached.isEmpty()) {
                return new Kline("kline", benchmark, aggregate(ensureHistoryDepth(benchmark, fundId, cached), period));
            }
            try {
                return new Kline("kline", benchmark, source.fetch(toSecid(benchmark), mapPeriod(period), KLINE_LIMIT)
                        .stream().map(KlineQueryHandler::toBar).toList());
            } catch (RuntimeException exception) {
                log.warn("基金 {} 指数 K 线拉取失败，降级净值走势", fundId, exception);
            }
        }
        return new Kline("nav", benchmark, navs.history(product.fundProductId(), Instant.parse(HISTORY_START),
                Instant.parse(HISTORY_END)).stream().map(nav -> new Bar(nav.navDate(), null,
                nav.accumulatedNav(), null, null, 0L)).toList());
    }

    /**
     * 本地日K缓存不足以支撑月K指标（MACD 需 34 根月K，约 740 根日线）时，
     * 回拉一次全量窗口合并入库实现自愈。24 小时内最多尝试一次，避免源历史本身不足时反复打远程。
     */
    private List<IndexKlineQueryHandler.Bar> ensureHistoryDepth(String benchmark, long fundId,
            List<IndexKlineQueryHandler.Bar> cached) {
        if (cached.size() >= MIN_DAILY_BARS) return cached;
        Instant lastAttempt = backfillAttemptedAt.get(benchmark);
        if (lastAttempt != null && lastAttempt.plus(BACKFILL_RETRY_INTERVAL).isAfter(Instant.now())) return cached;
        backfillAttemptedAt.put(benchmark, Instant.now());
        try {
            List<IndexKlineSourceGateway.Bar> fetched = source.fetch(toSecid(benchmark), mapPeriod("daily"), KLINE_LIMIT);
            if (fetched.isEmpty() || !fetched.getFirst().tradeDate().isBefore(cached.getFirst().tradeDate())) {
                return cached;
            }
            klineCommands.upsert(benchmark, fetched.stream().map(bar -> new IndexKlineCommandHandler.Bar(
                    bar.tradeDate(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume())).toList());
            log.info("指数 {} 日K缓存 {} 根不足以支撑月K指标，已回补至 {} 根", benchmark, cached.size(), fetched.size());
            List<IndexKlineQueryHandler.Bar> refreshed = cachedKlines.findAll(benchmark);
            return refreshed.isEmpty() ? cached : refreshed;
        } catch (RuntimeException exception) {
            log.warn("基金 {} 指数 {} 日K历史回补失败，沿用现有缓存", fundId, benchmark, exception);
            return cached;
        }
    }

    private static boolean isIndexLike(OwnedFundProductGateway.ProductType type) {
        return type == OwnedFundProductGateway.ProductType.ETF || type == OwnedFundProductGateway.ProductType.INDEX
                || type == OwnedFundProductGateway.ProductType.INDEX_ENHANCED;
    }

    private static List<Bar> aggregate(List<IndexKlineQueryHandler.Bar> daily, String period) {
        if (period == null || "daily".equalsIgnoreCase(period) || "d".equalsIgnoreCase(period)) {
            return daily.stream().map(KlineQueryHandler::toBar).toList();
        }
        boolean weekly = "weekly".equalsIgnoreCase(period) || "w".equalsIgnoreCase(period);
        Map<Instant, List<IndexKlineQueryHandler.Bar>> groups = new LinkedHashMap<>();
        for (var bar : daily) {
            var date = bar.tradeDate().atZone(ZoneOffset.UTC);
            Instant key = (weekly ? date.with(DayOfWeek.MONDAY) : date.withDayOfMonth(1))
                    .truncatedTo(java.time.temporal.ChronoUnit.DAYS).toInstant();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bar);
        }
        return groups.values().stream().map(group -> {
            var first = group.getFirst();
            var last = group.getLast();
            BigDecimal high = group.stream().map(IndexKlineQueryHandler.Bar::high).filter(java.util.Objects::nonNull)
                    .max(BigDecimal::compareTo).orElse(null);
            BigDecimal low = group.stream().map(IndexKlineQueryHandler.Bar::low).filter(java.util.Objects::nonNull)
                    .min(BigDecimal::compareTo).orElse(null);
            long volume = group.stream().mapToLong(bar -> bar.volume() == null ? 0L : bar.volume()).sum();
            return new Bar(last.tradeDate(), first.open(), last.close(), high, low, volume);
        }).toList();
    }

    private static Bar toBar(IndexKlineQueryHandler.Bar bar) {
        return new Bar(bar.tradeDate(), bar.open(), bar.close(), bar.high(), bar.low(),
                bar.volume() == null ? 0L : bar.volume());
    }

    private static Bar toBar(IndexKlineSourceGateway.Bar bar) {
        return new Bar(bar.tradeDate(), bar.open(), bar.close(), bar.high(), bar.low(), bar.volume());
    }

    private static String toSecid(String indexCode) {
        int dot = indexCode.indexOf('.');
        if (dot <= 0 || dot == indexCode.length() - 1) return indexCode;
        String prefix = switch (indexCode.substring(dot + 1).toUpperCase(Locale.ROOT)) {
            case "SH" -> "1.";
            case "SZ" -> "0.";
            case "CSI" -> "2.";
            default -> null;
        };
        return prefix == null ? indexCode : prefix + indexCode.substring(0, dot);
    }

    private static String mapPeriod(String period) {
        if (period == null) return "101";
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "weekly", "w", "week" -> "102";
            case "monthly", "m", "month" -> "103";
            default -> "101";
        };
    }

    public record Kline(String chartType, String benchmark, List<Bar> bars) {}
    public record Bar(Instant date, BigDecimal open, BigDecimal close, BigDecimal high,
                      BigDecimal low, long volume) {}
}
