package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeValuationCacheGateway;
import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeValuationQueryHandler {
    private final RealtimeValuationCacheGateway cache;
    private final OwnedFundProductGateway products;

    public List<ValuationResult> findByFundCodes(Collection<String> fundCodes) {
        return cache.findByFundCodes(fundCodes).values().stream()
                .map(value -> new ValuationResult(value.fundCode(), value.estimatedChangePct(),
                        value.estimateTime(), value.baseNavDate(), value.status())).toList();
    }

    public Optional<IntradayResult> findIntraday(long legacyFundId) {
        return products.findOwned(legacyFundId).flatMap(product -> cache.findIntraday(product.fundCode()))
                .map(RealtimeValuationQueryHandler::toIntradayResult);
    }

    public Optional<IntradayResult> findIntradayForPortfolioFund(long portfolioFundId) {
        var product = products.findOwnedByPortfolioFundId(portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "组合基金不存在或已作废"));
        return cache.findIntraday(product.fundCode())
                .map(RealtimeValuationQueryHandler::toIntradayResult);
    }

    /** 批量读当日估值快照;缓存未命中的 code 不出现在 map 中。 */
    public Map<String, EstimateResult> findEstimates(Collection<String> fundCodes) {
        return cache.findEstimates(fundCodes).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> new EstimateResult(entry.getValue().estimatedChangePct(),
                                entry.getValue().estimateTime(), entry.getValue().baseNavDate())));
    }

    public Map<String, String> findEstimateStatuses(Collection<String> fundCodes) {
        return cache.findEstimateStatuses(fundCodes);
    }

    public String findEstimateStatus(String fundCode) {
        return cache.findEstimateStatus(fundCode);
    }

    private static IntradayResult toIntradayResult(RealtimeValuationCacheGateway.Intraday value) {
        return new IntradayResult(value.estimateDate(), value.baseNav(), value.points().stream()
                .map(point -> new IntradayPoint(point.time(), point.nav())).toList(),
                value.tradingSessions().stream()
                        .map(session -> new IntradaySession(session.start(), session.end())).toList());
    }

    public record ValuationResult(String fundCode, java.math.BigDecimal estimatedChangePct,
                                  String estimateTime, String baseNavDate, String status) {}
    public record EstimateResult(java.math.BigDecimal estimatedChangePct, String estimateTime, String baseNavDate) {}
    public record IntradayResult(String estimateDate, java.math.BigDecimal baseNav, List<IntradayPoint> points,
                                 List<IntradaySession> tradingSessions) {}
    public record IntradayPoint(String time, java.math.BigDecimal nav) {}
    public record IntradaySession(String start, String end) {}
}
