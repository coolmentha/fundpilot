package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 腾讯证券日线响应解析器。 */
public final class TencentJsParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TencentJsParser() {
    }

    /**
     * 解析 {@code kline_dayqfq={...}} 中指定指数的日线。
     * <p>腾讯与 AKShare 返回字段顺序为：日期、开盘、收盘、最高、最低、成交量。
     * 空 {@code day} 表示该源不覆盖此指数，交给降级链继续处理。
     */
    public static IndexKline parseIndexKline(String raw, String symbol) {
        if (raw == null || raw.isBlank()) {
            return new IndexKline(List.of());
        }
        String json = ScriptPayloadExtractor.assignedValueByPrefix(raw, "kline_dayqfq");
        if (json == null) {
            throw new IllegalStateException("腾讯指数 K 线响应缺少 kline_dayqfq: " + symbol);
        }
        try {
            JsonNode day = MAPPER.readTree(json).path("data").path(symbol).path("day");
            if (!day.isArray() || day.isEmpty()) {
                return new IndexKline(List.of());
            }
            List<IndexKline.Bar> bars = new ArrayList<>(day.size());
            for (JsonNode row : day) {
                if (!row.isArray() || row.size() < 6) {
                    continue;
                }
                try {
                    BigDecimal open = positiveDecimal(row.get(1));
                    BigDecimal close = positiveDecimal(row.get(2));
                    BigDecimal high = positiveDecimal(row.get(3));
                    BigDecimal low = positiveDecimal(row.get(4));
                    BigDecimal volume = new BigDecimal(row.get(5).asText());
                    if (open == null || close == null || high == null || low == null
                            || volume.signum() < 0) {
                        continue;
                    }
                    Instant date = LocalDate.parse(row.get(0).asText())
                            .atStartOfDay(ZoneOffset.UTC).toInstant();
                    bars.add(new IndexKline.Bar(date, open, close, high, low, volume.longValue()));
                } catch (RuntimeException ignored) {
                    // 单行损坏不应污染整条指数序列；全坏时由下方抛解析异常触发降级。
                }
            }
            if (bars.isEmpty()) {
                throw new IllegalStateException("腾讯指数 K 线解析后无有效日线: " + symbol);
            }
            bars.sort(Comparator.comparing(IndexKline.Bar::date));
            return new IndexKline(List.copyOf(bars));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("腾讯指数 K 线 JSON 解析失败: " + symbol, e);
        }
    }

    private static BigDecimal positiveDecimal(JsonNode node) {
        if (node == null || (!node.isNumber() && !node.isTextual())) {
            return null;
        }
        BigDecimal value = new BigDecimal(node.asText());
        return value.signum() > 0 ? value : null;
    }
}
