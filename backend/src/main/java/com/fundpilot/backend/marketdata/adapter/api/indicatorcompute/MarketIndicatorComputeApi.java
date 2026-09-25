package com.fundpilot.backend.marketdata.adapter.api.indicatorcompute;

import com.fundpilot.backend.marketdata.application.query.indicatorcompute.IndicatorComputeQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 指标按需计算的对模块外契约：现算指标取值与可计算种类清单。 */
@Component
@RequiredArgsConstructor
public class MarketIndicatorComputeApi {

    private final IndicatorComputeQueryHandler queries;

    /** 现算指标最近 count 个取值（按日期升序，最新在最后）；数据不足或指标不适用时返回空列表。 */
    public List<IndicatorPoint> recent(ComputeRequest request) {
        return queries.recent(new IndicatorComputeQueryHandler.IndicatorComputeRequest(request.fundProductId(),
                        request.indicatorCode(), request.params(), request.endExclusive(), request.count()))
                .stream().map(value -> new IndicatorPoint(value.asOf(), value.value())).toList();
    }

    /** 全部可现算的指标种类。 */
    public Set<String> supportedCodes() {
        return IndicatorComputeQueryHandler.supportedCodes();
    }

    public record ComputeRequest(long fundProductId, String indicatorCode, Map<String, Integer> params,
                                 Instant endExclusive, int count) {
    }

    public record IndicatorPoint(Instant asOf, BigDecimal value) {
    }
}