package com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyEtfSpotClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyEtfSpotParser;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsEtfSpotClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsEtfSpotParser;
import com.fundpilot.backend.platform.observability.MarketDataMetrics;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * ETF IOPV 估值备用源，对应 AKShare {@code fund_etf_spot_em} + {@code fund_etf_spot_ths}。
 *
 * <p>东方财富提供盘中 IOPV，同花顺提供最近确认单位净值和日期；两者配对后才计算
 * 相对最近确认净值的当日估算涨跌。LOF/ETF 交易价格不进入此服务。
 */
@Service
@RequiredArgsConstructor
public class EtfIopvEstimateService {

    private static final Logger log = LoggerFactory.getLogger(EtfIopvEstimateService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final EastmoneyEtfSpotClient eastmoneyEtfSpotClient;
    private final ThsEtfSpotClient thsEtfSpotClient;
    private final MarketDataMetrics metrics;
    private final Clock clock;
    private volatile Cache cache = Cache.empty();

    public FundEstimateResult fetchEstimateResult(String fundCode) {
        return fetchEstimateResult(fundCode, Instant.MAX);
    }

    public FundEstimateResult fetchEstimateResult(String fundCode, Instant deadline) {
        if (!looksLikeExchangeFund(fundCode)) {
            return FundEstimateResult.unavailable();
        }
        Cache current = load(deadline);
        FundEstimateSnapshot snapshot = current.rows().get(fundCode);
        if (snapshot != null) {
            return FundEstimateResult.available(snapshot);
        }
        return current.failureStatus() != null && current.failureStatus().isFailure()
                ? FundEstimateResult.failed(current.failureStatus())
                : FundEstimateResult.unavailable();
    }

    private Cache load(Instant deadline) {
        Instant now = clock.instant();
        Cache current = cache;
        if (current.complete() && current.expiresAt().isAfter(now)) {
            return current;
        }
        synchronized (this) {
            current = cache;
            now = clock.instant();
            if (current.complete() && current.expiresAt().isAfter(now)) {
                return current;
            }
            if (!clock.instant().isBefore(deadline)) {
                return current;
            }
            long startedAt = System.nanoTime();
            try {
                Map<String, ThsEtfSpotParser.BaseNav> baseNavs = ThsEtfSpotParser.parse(thsEtfSpotClient.fetchSpotRaw());
                if (baseNavs.isEmpty()) {
                    metrics.record("ThsEtfSpotClient", "fetchEstimateBaseNav", "empty", startedAt);
                    current = Cache.unavailable(clock.instant());
                } else {
                    metrics.record("ThsEtfSpotClient", "fetchEstimateBaseNav", "success", startedAt);
                    current = fetchQuotes(baseNavs, current, deadline);
                    if (current.complete()) {
                        metrics.record("EastmoneyEtfSpotClient", "fetchEstimateBatch",
                                current.rows().isEmpty() ? "empty" : "success", startedAt);
                    }
                }
            } catch (RetryableException ex) {
                current = Cache.failed(clock.instant(), EstimateStatus.TIMEOUT);
                metrics.record("EastmoneyEtfSpotClient", "fetchEstimateBatch", "timeout", startedAt);
                log.debug("拉取 ETF IOPV 估值超时: {}", ex.getMessage());
            } catch (IllegalStateException ex) {
                current = Cache.failed(clock.instant(), EstimateStatus.PARSE_ERROR);
                metrics.record("EastmoneyEtfSpotClient", "fetchEstimateBatch", "parse_error", startedAt);
                log.debug("解析 ETF IOPV 估值失败: {}", ex.getMessage());
            } catch (RuntimeException ex) {
                current = Cache.unavailable(clock.instant());
                metrics.record("EastmoneyEtfSpotClient", "fetchEstimateBatch", "failure", startedAt);
                log.debug("拉取 ETF IOPV 估值失败: {}", ex.getMessage());
            }
            cache = current;
            return current;
        }
    }

    private Cache fetchQuotes(Map<String, ThsEtfSpotParser.BaseNav> baseNavs, Cache current, Instant deadline) {
        int firstPage = current.complete() ? 1 : current.nextPage();
        Map<String, FundEstimateSnapshot> rows = new HashMap<>(current.complete() ? Map.of() : current.rows());
        for (int page = firstPage; page <= MAX_PAGES; page++) {
            if (!clock.instant().isBefore(deadline)) {
                return Cache.partial(rows, page);
            }
            EastmoneyEtfSpotParser.Page pageData = EastmoneyEtfSpotParser.parse(
                    eastmoneyEtfSpotClient.fetchSpotPageRaw(page));
            if (pageData.quotes().isEmpty()) {
                return Cache.complete(rows, clock.instant());
            }
            rows.putAll(join(pageData.quotes(), baseNavs));
            int totalPages = Math.max(1, (pageData.total() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page >= totalPages) {
                return Cache.complete(rows, clock.instant());
            }
        }
        return Cache.complete(rows, clock.instant());
    }

    private Map<String, FundEstimateSnapshot> join(
            Map<String, EastmoneyEtfSpotParser.Quote> quotes,
            Map<String, ThsEtfSpotParser.BaseNav> baseNavs) {
        Map<String, FundEstimateSnapshot> result = new HashMap<>();
        for (EastmoneyEtfSpotParser.Quote quote : quotes.values()) {
            ThsEtfSpotParser.BaseNav baseNav = baseNavs.get(quote.code());
            if (baseNav == null) {
                continue;
            }
            BigDecimal changePct = quote.iopv().subtract(baseNav.nav())
                    .divide(baseNav.nav(), MathContext.DECIMAL64);
            Instant updatedAt = quote.updatedAt() == null ? clock.instant() : quote.updatedAt();
            String estimateDate = quote.dataDate() == null
                    ? updatedAt.atZone(CHINA_ZONE).toLocalDate().toString()
                    : LocalDate.parse(formatDate(quote.dataDate())).toString();
            String estimateTime = LocalTime.ofInstant(updatedAt, CHINA_ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));
            result.put(quote.code(), new FundEstimateSnapshot(changePct, estimateDate + " " + estimateTime,
                    baseNav.navDate()));
        }
        return result;
    }

    private static String formatDate(String value) {
        return value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6);
    }

    private static boolean looksLikeExchangeFund(String fundCode) {
        return fundCode != null && fundCode.matches("(?:15|5\\d)\\d{4}");
    }

    private record Cache(Map<String, FundEstimateSnapshot> rows, Instant expiresAt, EstimateStatus failureStatus,
                         int nextPage) {
        private static Cache empty() {
            return new Cache(Map.of(), Instant.MIN, null, 1);
        }

        private static Cache unavailable(Instant completedAt) {
            return new Cache(Map.of(), completedAt.plus(CACHE_TTL), null, 0);
        }

        private static Cache failed(Instant completedAt, EstimateStatus status) {
            return new Cache(Map.of(), completedAt.plus(CACHE_TTL), status, 0);
        }

        private static Cache partial(Map<String, FundEstimateSnapshot> rows, int nextPage) {
            return new Cache(Map.copyOf(rows), Instant.MIN, null, nextPage);
        }

        private static Cache complete(Map<String, FundEstimateSnapshot> rows, Instant completedAt) {
            return new Cache(Map.copyOf(rows), completedAt.plus(CACHE_TTL), null, 0);
        }

        private boolean complete() {
            return nextPage == 0;
        }
    }
}
