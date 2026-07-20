package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.EastmoneyFundGzClient;
import com.fundpilot.backend.market.client.EastmoneyJsParser;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.ThsFundEstimateClient;
import com.fundpilot.backend.market.client.ThsJsParser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 基金盘中估值拉取服务(issue #36):东方财富 fundgz 失败时降级同花顺估值接口,
 * 供三态今日涨跌「估值阶段」使用(详见 ADR-0008 / issue #38)。
 *
 * <p>估值是短时态数据(盘中每分钟变化,当日实际净值落库后失效),后台刷新不落库。
 * 失败降级返 empty(估值拉不到不影响主流程,今日涨跌显示未知而非回退昨日值)。
 */
@Service
@RequiredArgsConstructor
public class FundEstimateService {

    private static final Logger log = LoggerFactory.getLogger(FundEstimateService.class);

    private final EastmoneyFundGzClient eastmoneyFundGzClient;
    private final ThsFundEstimateClient thsFundEstimateClient;
    private final MarketDataMetrics metrics;

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
        EstimateStatus eastmoneyFailure = EstimateStatus.UNAVAILABLE;
        try {
            String raw = eastmoneyFundGzClient.fetchGzRaw(fundCode);
            FundEstimateSnapshot snapshot = EastmoneyJsParser.parseFundGz(raw);
            metrics.record("EastmoneyFundGzClient", "fetchEstimate",
                    snapshot == null ? "empty" : "success", startedAt);
            if (snapshot != null) {
                return FundEstimateResult.available(snapshot);
            }
        } catch (IllegalStateException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "parse_error", startedAt);
            eastmoneyFailure = EstimateStatus.PARSE_ERROR;
            log.debug("解析基金 {} 东方财富盘中估值失败,尝试同花顺: {}", fundCode, ex.getMessage());
        } catch (feign.RetryableException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "timeout", startedAt);
            eastmoneyFailure = EstimateStatus.TIMEOUT;
            log.debug("拉取基金 {} 东方财富盘中估值超时,尝试同花顺: {}", fundCode, ex.getMessage());
        } catch (RuntimeException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "failure", startedAt);
            log.debug("拉取基金 {} 东方财富盘中估值不可用,尝试同花顺: {}", fundCode, ex.getMessage());
        }

        long thsStartedAt = System.nanoTime();
        try {
            FundEstimateSnapshot snapshot = ThsJsParser.parseFundEstimate(
                    thsFundEstimateClient.fetchEstimateRaw(fundCode));
            metrics.record("ThsFundEstimateClient", "fetchEstimate",
                    snapshot == null ? "empty" : "success", thsStartedAt);
            return snapshot == null ? FundEstimateResult.unavailable() : FundEstimateResult.available(snapshot);
        } catch (IllegalStateException ex) {
            metrics.record("ThsFundEstimateClient", "fetchEstimate", "parse_error", thsStartedAt);
            log.warn("解析基金 {} 同花顺盘中估值失败: {}", fundCode, ex.getMessage());
            return FundEstimateResult.failed(EstimateStatus.PARSE_ERROR);
        } catch (feign.RetryableException ex) {
            metrics.record("ThsFundEstimateClient", "fetchEstimate", "timeout", thsStartedAt);
            log.warn("拉取基金 {} 同花顺盘中估值超时: {}", fundCode, ex.getMessage());
            return FundEstimateResult.failed(eastmoneyFailure == EstimateStatus.PARSE_ERROR
                    ? EstimateStatus.PARSE_ERROR : EstimateStatus.TIMEOUT);
        } catch (RuntimeException ex) {
            metrics.record("ThsFundEstimateClient", "fetchEstimate", "failure", thsStartedAt);
            log.warn("拉取基金 {} 同花顺盘中估值不可用: {}", fundCode, ex.getMessage());
            return eastmoneyFailure.isFailure()
                    ? FundEstimateResult.failed(eastmoneyFailure) : FundEstimateResult.unavailable();
        }
    }
}
