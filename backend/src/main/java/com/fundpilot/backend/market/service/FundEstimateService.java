package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.EastmoneyFundGzClient;
import com.fundpilot.backend.market.client.EastmoneyJsParser;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 基金盘中估值拉取服务(issue #36):调 fundgz 接口取盘中估算涨跌幅,
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
        try {
            String raw = eastmoneyFundGzClient.fetchGzRaw(fundCode);
            FundEstimateSnapshot snapshot = EastmoneyJsParser.parseFundGz(raw);
            metrics.record("EastmoneyFundGzClient", "fetchEstimate",
                    snapshot == null ? "empty" : "success", startedAt);
            return snapshot == null ? FundEstimateResult.unavailable() : FundEstimateResult.available(snapshot);
        } catch (IllegalStateException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "parse_error", startedAt);
            log.warn("解析基金 {} 盘中估值失败: {}", fundCode, ex.getMessage());
            return FundEstimateResult.failed(EstimateStatus.PARSE_ERROR);
        } catch (feign.RetryableException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "timeout", startedAt);
            log.warn("拉取基金 {} 盘中估值超时: {}", fundCode, ex.getMessage());
            return FundEstimateResult.failed(EstimateStatus.TIMEOUT);
        } catch (RuntimeException ex) {
            metrics.record("EastmoneyFundGzClient", "fetchEstimate", "failure", startedAt);
            log.warn("拉取基金 {} 盘中估值不可用: {}", fundCode, ex.getMessage());
            return FundEstimateResult.unavailable();
        }
    }
}
