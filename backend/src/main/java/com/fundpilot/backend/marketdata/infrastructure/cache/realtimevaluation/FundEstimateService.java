package com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation;

import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyFundGzClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyFundEstimatePageClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyFundEstimatePageParser;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyJsParser;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimatePageRow;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundIntradayChart;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsFundEstimateClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsJsParser;
import com.fundpilot.backend.platform.observability.MarketDataMetrics;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基金盘中估值拉取服务(issue #36):同花顺估值接口失败时降级 AKShare 参考的东方财富静态页，
 * ETF 再尝试 IOPV，最后兼容回退旧 fundgz，供三态今日涨跌「估值阶段」使用
 * (详见 ADR-0008 / issue #38)。
 *
 * <p>估值是短时态数据(盘中每分钟变化,当日实际净值落库后失效),后台刷新不落库。
 * 失败降级返 empty(估值拉不到不影响主流程,今日涨跌显示未知而非回退昨日值)。
 */
@Service
@RequiredArgsConstructor
public class FundEstimateService {

    private static final Logger log = LoggerFactory.getLogger(FundEstimateService.class);
    private static final Duration THS_FAILURE_BACKOFF = Duration.ofMinutes(5);
    private static final Duration AKSHARE_PAGE_CACHE_TTL = Duration.ofMinutes(1);
    private static final int AKSHARE_MAX_PAGES = 99;
    private static final DateTimeFormatter ESTIMATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final EastmoneyFundGzClient eastmoneyFundGzClient;
    private final EastmoneyFundEstimatePageClient eastmoneyFundEstimatePageClient;
    private final EtfIopvEstimateService etfIopvEstimateService;
    private final ThsFundEstimateClient thsFundEstimateClient;
    private final MarketDataMetrics metrics;
    private final Clock clock;
    private final Map<String, Instant> thsRetryAfter = new ConcurrentHashMap<>();
    private volatile AksharePageCache aksharePageCache = AksharePageCache.empty();

    /**
     * @param fundCode 基金代码
     * @return 盘中估值快照(含估算涨跌幅);拉取失败或解析失败返 empty
     */
    public Optional<FundEstimateSnapshot> fetchEstimate(String fundCode) {
        return Optional.ofNullable(fetchEstimateResult(fundCode).snapshot());
    }

    public FundEstimateResult fetchEstimateResult(String fundCode) {
        return fetchEstimateResult(fundCode, Instant.MAX, fundCode == null ? Set.of() : Set.of(fundCode));
    }

    public FundEstimateResult fetchEstimateResult(String fundCode, Instant deadline) {
        return fetchEstimateResult(fundCode, deadline, fundCode == null ? Set.of() : Set.of(fundCode));
    }

    public FundEstimateResult fetchEstimateResult(String fundCode, Instant deadline, Set<String> targetCodes) {
        long startedAt = System.nanoTime();
        if (fundCode == null || fundCode.isBlank()) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "empty", startedAt);
            return FundEstimateResult.unavailable();
        }
        EstimateStatus thsFailure = EstimateStatus.UNAVAILABLE;
        if (shouldTryThs(fundCode)) {
            try {
                FundIntradayChart intradayChart = ThsJsParser.parseFundIntradayChart(
                        thsFundEstimateClient.fetchEstimateRaw(fundCode));
                FundEstimateSnapshot snapshot = intradayChart == null ? null : ThsJsParser.parseFundEstimateFrom(intradayChart);
                metrics.record("ThsFundEstimateClient", "fetchEstimate",
                        snapshot == null ? "empty" : "success", startedAt);
                if (snapshot != null) {
                    thsRetryAfter.remove(fundCode);
                    return FundEstimateResult.available(snapshot, intradayChart);
                }
            } catch (IllegalStateException ex) {
                metrics.record("ThsFundEstimateClient", "fetchEstimate", "parse_error", startedAt);
                thsFailure = EstimateStatus.PARSE_ERROR;
                recordThsFailure(fundCode);
                log.warn("解析基金 {} 同花顺盘中估值失败,尝试东方财富", fundCode, ex);
            } catch (feign.RetryableException ex) {
                metrics.record("ThsFundEstimateClient", "fetchEstimate", "timeout", startedAt);
                thsFailure = EstimateStatus.TIMEOUT;
                recordThsFailure(fundCode);
                log.warn("拉取基金 {} 同花顺盘中估值超时,尝试东方财富", fundCode, ex);
            } catch (RuntimeException ex) {
                metrics.record("ThsFundEstimateClient", "fetchEstimate", "failure", startedAt);
                recordThsFailure(fundCode);
                log.warn("拉取基金 {} 同花顺盘中估值不可用,尝试东方财富", fundCode, ex);
            }
        }

        EstimateStatus akshareFailure = EstimateStatus.UNAVAILABLE;
        EstimateStatus etfFailure = EstimateStatus.UNAVAILABLE;
        ensureBefore(deadline);
        AksharePageCache akshareCache = aksharePageCache;
        FundEstimatePageRow akshareRow = akshareCache.expiresAt().isAfter(clock.instant())
                ? akshareCache.rows().get(fundCode) : null;
        if (akshareRow != null) {
            return FundEstimateResult.available(toSnapshot(akshareRow));
        }
        akshareCache = loadAksharePageCache(deadline, targetCodes, fundCode);
        akshareRow = akshareCache.rows().get(fundCode);
        if (akshareRow != null) {
            return FundEstimateResult.available(toSnapshot(akshareRow));
        }
        if (akshareCache.failureStatus() != null) {
            akshareFailure = akshareCache.failureStatus();
        }

        ensureBefore(deadline);
        FundEstimateResult etfResult = etfIopvEstimateService.fetchEstimateResult(fundCode, deadline);
        if (etfResult.status() == EstimateStatus.AVAILABLE) {
            return etfResult;
        }
        if (etfResult.status().isFailure()) {
            etfFailure = etfResult.status();
        }

        ensureBefore(deadline);
        long eastmoneyStartedAt = System.nanoTime();
        try {
            FundEstimateSnapshot snapshot = EastmoneyJsParser.parseFundGz(eastmoneyFundGzClient.fetchGzRaw(fundCode));
            metrics.record("EastmoneyFundGzClient", "fetchEstimate",
                    snapshot == null ? "empty" : "success", eastmoneyStartedAt);
            return snapshot == null && firstFailure(thsFailure, akshareFailure, etfFailure).isFailure()
                    ? FundEstimateResult.failed(firstFailure(thsFailure, akshareFailure, etfFailure))
                    : snapshot == null ? FundEstimateResult.unavailable() : FundEstimateResult.available(snapshot);
        } catch (IllegalStateException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "parse_error", eastmoneyStartedAt);
            log.debug("解析基金 {} 东方财富盘中估值失败", fundCode, ex);
            return FundEstimateResult.failed(firstFailure(thsFailure, akshareFailure, etfFailure,
                    EstimateStatus.PARSE_ERROR));
        } catch (RetryableException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "timeout", eastmoneyStartedAt);
            log.debug("拉取基金 {} 东方财富盘中估值超时", fundCode, ex);
            return FundEstimateResult.failed(firstFailure(thsFailure, akshareFailure, etfFailure,
                    EstimateStatus.TIMEOUT));
        } catch (RuntimeException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "failure", eastmoneyStartedAt);
            log.debug("拉取基金 {} 东方财富盘中估值不可用", fundCode, ex);
            EstimateStatus failure = firstFailure(thsFailure, akshareFailure, etfFailure);
            return failure.isFailure() ? FundEstimateResult.failed(failure) : FundEstimateResult.unavailable();
        }
    }

    private AksharePageCache loadAksharePageCache(Instant deadline, Set<String> targetCodes, String currentFundCode) {
        Instant now = clock.instant();
        AksharePageCache cached = aksharePageCache;
        if (cached.complete() && cached.expiresAt().isAfter(now)) {
            return cached;
        }
        synchronized (this) {
            cached = aksharePageCache;
            now = clock.instant();
            if (cached.complete() && cached.expiresAt().isAfter(now)) {
                return cached;
            }
            long startedAt = System.nanoTime();
            AksharePageCache loaded;
            try {
                boolean resume = !cached.complete() && cached.expiresAt().isAfter(now);
                int firstPage = resume ? cached.nextPage() : 1;
                Map<String, FundEstimatePageRow> rows = new HashMap<>(
                        resume ? cached.rows() : Map.of());
                for (int page = firstPage; page <= AKSHARE_MAX_PAGES; page++) {
                    if (!clock.instant().isBefore(deadline)) {
                        loaded = AksharePageCache.partial(rows, page, clock.instant());
                        aksharePageCache = loaded;
                        return loaded;
                    }
                    String raw;
                    try {
                        raw = eastmoneyFundEstimatePageClient.fetchPageRaw(page);
                    } catch (FeignException ex) {
                        if (ex.status() == 404) {
                            loaded = AksharePageCache.complete(rows, clock.instant());
                            metrics.record("EastmoneyFundEstimatePageClient", "fetchEstimateBatch",
                                    rows.isEmpty() ? "empty" : "success", startedAt);
                            aksharePageCache = loaded;
                            return loaded;
                        }
                        throw ex;
                    }
                    Map<String, FundEstimatePageRow> pageRows = EastmoneyFundEstimatePageParser.parse(raw);
                    if (pageRows.isEmpty()) {
                        loaded = AksharePageCache.complete(rows, clock.instant());
                        metrics.record("EastmoneyFundEstimatePageClient", "fetchEstimateBatch",
                                rows.isEmpty() ? "empty" : "success", startedAt);
                        aksharePageCache = loaded;
                        return loaded;
                    }
                    pageRows.forEach((code, row) -> {
                        if (targetCodes.contains(code)) {
                            rows.put(code, row);
                        }
                    });
                    if (rows.containsKey(currentFundCode) || rows.keySet().containsAll(targetCodes)) {
                        loaded = AksharePageCache.partial(rows, page + 1, clock.instant());
                        aksharePageCache = loaded;
                        return loaded;
                    }
                }
                loaded = AksharePageCache.complete(rows, clock.instant());
                metrics.record("EastmoneyFundEstimatePageClient", "fetchEstimateBatch",
                        rows.isEmpty() ? "empty" : "success", startedAt);
            } catch (RetryableException ex) {
                loaded = failedPageCache(clock.instant(), EstimateStatus.TIMEOUT);
                metrics.record("EastmoneyFundEstimatePageClient", "fetchEstimateBatch", "timeout", startedAt);
                log.debug("拉取东方财富静态基金估值页超时", ex);
            } catch (IllegalStateException ex) {
                loaded = failedPageCache(clock.instant(), EstimateStatus.PARSE_ERROR);
                metrics.record("EastmoneyFundEstimatePageClient", "fetchEstimateBatch", "parse_error", startedAt);
                log.debug("解析东方财富静态基金估值页失败", ex);
            } catch (RuntimeException ex) {
                loaded = failedPageCache(clock.instant(), EstimateStatus.UNAVAILABLE);
                metrics.record("EastmoneyFundEstimatePageClient", "fetchEstimateBatch", "failure", startedAt);
                log.debug("拉取东方财富静态基金估值页失败", ex);
            }
            aksharePageCache = loaded;
            return loaded;
        }
    }

    private AksharePageCache failedPageCache(Instant completedAt, EstimateStatus status) {
        return new AksharePageCache(Map.of(), completedAt.plus(AKSHARE_PAGE_CACHE_TTL), status, 0);
    }

    private FundEstimateSnapshot toSnapshot(FundEstimatePageRow row) {
        String estimateTime = row.estimateDate() + " "
                + LocalTime.now(clock.withZone(ChinaTradingDate.ZONE)).format(ESTIMATE_TIME_FORMATTER);
        return new FundEstimateSnapshot(row.estimatedChangePct(), estimateTime, row.baseNavDate());
    }

    private static EstimateStatus firstFailure(EstimateStatus... statuses) {
        for (EstimateStatus status : statuses) {
            if (status != null && status.isFailure()) {
                return status;
            }
        }
        return EstimateStatus.UNAVAILABLE;
    }

    private boolean shouldTryThs(String fundCode) {
        Instant retryAfter = thsRetryAfter.get(fundCode);
        if (retryAfter == null || !retryAfter.isAfter(clock.instant())) {
            thsRetryAfter.remove(fundCode);
            return true;
        }
        return false;
    }

    private void recordThsFailure(String fundCode) {
        thsRetryAfter.put(fundCode, clock.instant().plus(THS_FAILURE_BACKOFF));
    }

    private void ensureBefore(Instant deadline) {
        if (!clock.instant().isBefore(deadline)) {
            throw new CancellationException("基金估值刷新达到本轮截止时间");
        }
    }

    private record AksharePageCache(Map<String, FundEstimatePageRow> rows, Instant expiresAt,
                                    EstimateStatus failureStatus, int nextPage) {

        private static AksharePageCache empty() {
            return new AksharePageCache(Map.of(), Instant.MIN, null, 1);
        }

        private static AksharePageCache partial(Map<String, FundEstimatePageRow> rows, int nextPage,
                                                Instant completedAt) {
            return new AksharePageCache(Map.copyOf(rows), completedAt.plus(AKSHARE_PAGE_CACHE_TTL), null, nextPage);
        }

        private static AksharePageCache complete(Map<String, FundEstimatePageRow> rows, Instant completedAt) {
            return new AksharePageCache(Map.copyOf(rows), completedAt.plus(AKSHARE_PAGE_CACHE_TTL), null, 0);
        }

        private boolean complete() {
            return nextPage == 0;
        }
    }
}
