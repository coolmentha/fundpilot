package com.fundpilot.backend.marketdata.infrastructure.gateway.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeValuationCacheGateway;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeValuationCacheGatewayImpl implements RealtimeValuationCacheGateway {
    private static final String KEY = "fundpilot:market-realtime:v1";
    private static final DateTimeFormatter ESTIMATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();
    private final StringRedisTemplate redis;
    private final Clock clock;

    @Override
    public Map<String, Valuation> findByFundCodes(Collection<String> fundCodes) {
        try {
            String json = redis.opsForValue().get(KEY);
            if (json == null) return Map.of();
            JsonNode root = MAPPER.readTree(json);
            JsonNode estimates = root.path("estimates");
            JsonNode statuses = root.path("estimateStatuses");
            Map<String, Valuation> result = new LinkedHashMap<>();
            for (String code : fundCodes) {
                JsonNode estimate = estimates.path(code);
                String status = statuses.path(code).asText("NOT_ATTEMPTED");
                if ("AVAILABLE".equals(status) && !isToday(text(estimate, "estimateTime"))) {
                    status = "STALE";
                }
                BigDecimal change = estimate.path("estimatedChangePct").isNumber()
                        ? estimate.path("estimatedChangePct").decimalValue() : null;
                result.put(code, new Valuation(code, change, text(estimate, "estimateTime"),
                        text(estimate, "baseNavDate"), status));
            }
            return Map.copyOf(result);
        } catch (Exception exception) {
            log.warn("读取实时估值缓存失败: key={}, requestedFundCount={}", KEY, fundCodes.size(), exception);
            return Map.of();
        }
    }

    @Override
    public Optional<Intraday> findIntraday(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) return Optional.empty();
        try {
            String json = redis.opsForValue().get(KEY);
            if (json == null) return Optional.empty();
            JsonNode root = MAPPER.readTree(json);
            JsonNode estimate = root.path("estimates").path(fundCode);
            JsonNode chart = root.path("intradayCharts").path(fundCode);
            if (estimate.isMissingNode() || !"AVAILABLE".equals(root.path("estimateStatuses").path(fundCode)
                    .asText("NOT_ATTEMPTED")) || !isToday(text(estimate, "estimateTime"))
                    || chart.isMissingNode()) return Optional.empty();
            java.util.List<Point> points = new java.util.ArrayList<>();
            for (JsonNode point : chart.path("points")) {
                if (point.path("time").isTextual() && point.path("nav").isNumber()) {
                    points.add(new Point(point.path("time").asText(), point.path("nav").decimalValue()));
                }
            }
            if (points.size() < 2 || !chart.path("estimateDate").isTextual() || !chart.path("baseNav").isNumber()) {
                return Optional.empty();
            }
            return Optional.of(new Intraday(chart.path("estimateDate").asText(),
                    chart.path("baseNav").decimalValue(), List.copyOf(points)));
        } catch (Exception exception) {
            log.warn("读取基金分时缓存失败: fundCode={}", fundCode, exception);
            return Optional.empty();
        }
    }

    /** 估值时间是否属于今日(北京自然日)；Redis 中昨日 AVAILABLE 不得再当今日数据返回。 */
    private boolean isToday(String estimateTime) {
        if (estimateTime == null) {
            return false;
        }
        try {
            LocalDateTime estimateTimeLabel = LocalDateTime.parse(estimateTime, ESTIMATE_TIME_FORMATTER);
            return ChinaTradingDate.toUtcDate(estimateTimeLabel.atZone(ChinaTradingDate.ZONE).toInstant())
                    .equals(ChinaTradingDate.toUtcDate(clock.instant()));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
