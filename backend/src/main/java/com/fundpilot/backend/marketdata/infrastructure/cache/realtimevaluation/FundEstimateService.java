package com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyFundGzClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyJsParser;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundIntradayChart;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsFundEstimateClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsJsParser;
import com.fundpilot.backend.platform.observability.MarketDataMetrics;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基金盘中估值拉取服务(issue #36):同花顺估值接口失败时降级东方财富 fundgz,
 * 供三态今日涨跌「估值阶段」使用(详见 ADR-0008 / issue #38)。
 *
 * <p>估值是短时态数据(盘中每分钟变化,当日实际净值落库后失效),后台刷新不落库。
 * 失败降级返 empty(估值拉不到不影响主流程,今日涨跌显示未知而非回退昨日值)。
 */
@Service
@RequiredArgsConstructor
public class FundEstimateService {

    private static final Logger log = LoggerFactory.getLogger(FundEstimateService.class);
    private static final Duration THS_FAILURE_BACKOFF = Duration.ofMinutes(5);

    private final EastmoneyFundGzClient eastmoneyFundGzClient;
    private final ThsFundEstimateClient thsFundEstimateClient;
    private final MarketDataMetrics metrics;
    private final Clock clock;
    private final Map<String, Instant> thsRetryAfter = new ConcurrentHashMap<>();

    /**
     * @param fundCode 基金代码
     * @return 盘中估值快照(含估算涨跌幅);拉取失败或解析失败返 empty
     */
    public Optional<FundEstimateSnapshot> fetchEstimate(String fundCode) {
        return Optional.ofNullable(fetchEstimateResult(fundCode).snapshot());
    }

    public FundEstimateResult fetchEstimateResult(String fundCode) {
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
                log.warn("解析基金 {} 同花顺盘中估值失败,尝试东方财富: {}", fundCode, ex.getMessage());
            } catch (feign.RetryableException ex) {
                metrics.record("ThsFundEstimateClient", "fetchEstimate", "timeout", startedAt);
                thsFailure = EstimateStatus.TIMEOUT;
                recordThsFailure(fundCode);
                log.warn("拉取基金 {} 同花顺盘中估值超时,尝试东方财富: {}", fundCode, ex.getMessage());
            } catch (RuntimeException ex) {
                metrics.record("ThsFundEstimateClient", "fetchEstimate", "failure", startedAt);
                recordThsFailure(fundCode);
                log.warn("拉取基金 {} 同花顺盘中估值不可用,尝试东方财富: {}", fundCode, ex.getMessage());
            }
        }

        long eastmoneyStartedAt = System.nanoTime();
        try {
            FundEstimateSnapshot snapshot = EastmoneyJsParser.parseFundGz(eastmoneyFundGzClient.fetchGzRaw(fundCode));
            metrics.record("EastmoneyFundGzClient", "fetchEstimate",
                    snapshot == null ? "empty" : "success", eastmoneyStartedAt);
            return snapshot == null && thsFailure.isFailure()
                    ? FundEstimateResult.failed(thsFailure)
                    : snapshot == null ? FundEstimateResult.unavailable() : FundEstimateResult.available(snapshot);
        } catch (IllegalStateException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "parse_error", eastmoneyStartedAt);
            log.debug("解析基金 {} 东方财富盘中估值失败: {}", fundCode, ex.getMessage());
            return FundEstimateResult.failed(EstimateStatus.PARSE_ERROR);
        } catch (feign.RetryableException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "timeout", eastmoneyStartedAt);
            log.debug("拉取基金 {} 东方财富盘中估值超时: {}", fundCode, ex.getMessage());
            return FundEstimateResult.failed(thsFailure == EstimateStatus.PARSE_ERROR
                    ? EstimateStatus.PARSE_ERROR : EstimateStatus.TIMEOUT);
        } catch (RuntimeException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "failure", eastmoneyStartedAt);
            log.debug("拉取基金 {} 东方财富盘中估值不可用: {}", fundCode, ex.getMessage());
            return thsFailure.isFailure() ? FundEstimateResult.failed(thsFailure) : FundEstimateResult.unavailable();
        }
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
}
