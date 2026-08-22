package com.fundpilot.backend.marketdata.adapter.api.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeValuationQueryHandler;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基金当日估值缓存对外契约(模块边界,ADR-0022):供 fund 等模块读取
 * fundgz 估值快照与刷新状态,不暴露内部缓存实现类型。
 */
@Component
@RequiredArgsConstructor
public class MarketEstimateApi {
    private final RealtimeValuationQueryHandler queries;

    /** 批量读估值快照;缓存未命中的 code 不出现在 map 中。 */
    public Map<String, Snapshot> getEstimates(Collection<String> fundCodes) {
        return queries.findEstimates(fundCodes).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new Snapshot(entry.getValue().estimatedChangePct(),
                                entry.getValue().estimateTime(), entry.getValue().baseNavDate())));
    }

    public Map<String, Status> getEstimateStatuses(Collection<String> fundCodes) {
        return queries.findEstimateStatuses(fundCodes).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Status.valueOf(entry.getValue())));
    }

    public Status getEstimateStatus(String fundCode) {
        return Status.valueOf(queries.findEstimateStatus(fundCode));
    }

    public enum Status {
        NOT_ATTEMPTED, AVAILABLE, UNAVAILABLE, STALE, TIMEOUT, PARSE_ERROR;

        public boolean isFailure() {
            return this == TIMEOUT || this == PARSE_ERROR;
        }
    }

    public record Snapshot(BigDecimal estimatedChangePct, String estimateTime, String baseNavDate) {}
}
