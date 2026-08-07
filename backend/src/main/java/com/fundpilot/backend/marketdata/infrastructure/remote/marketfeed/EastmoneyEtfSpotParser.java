package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 解析 AKShare {@code fund_etf_spot_em} 使用的东方财富 ETF JSON。 */
public final class EastmoneyEtfSpotParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EastmoneyEtfSpotParser() {
    }

    /**
     * @return 分页总数及 ETF 的 IOPV 行；空 data/空 diff 表示源暂时没有数据
     */
    public static Page parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Page.empty();
        }
        try {
            JsonNode data = MAPPER.readTree(raw).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Page.empty();
            }
            JsonNode diff = data.path("diff");
            if (diff.isMissingNode() || diff.isNull()) {
                return Page.empty();
            }
            if (!diff.isObject() && !diff.isArray()) {
                throw new IllegalStateException("东方财富 ETF 响应 diff 结构错误");
            }

            Map<String, Quote> quotes = new HashMap<>();
            Iterator<JsonNode> rows = diff.elements();
            while (rows.hasNext()) {
                JsonNode row = rows.next();
                String code = row.path("f12").asText("");
                BigDecimal iopv = decimal(row.path("f441"));
                if (!code.matches("\\d{6}") || iopv == null || iopv.signum() <= 0) {
                    continue;
                }
                Instant updatedAt = epochSeconds(row.path("f124"));
                String dataDate = row.path("f297").asText("");
                quotes.put(code, new Quote(code, iopv, updatedAt,
                        dataDate.matches("\\d{8}") ? dataDate : null));
            }
            int total = data.path("total").asInt(quotes.size());
            return new Page(Math.max(total, quotes.size()), Map.copyOf(quotes));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("东方财富 ETF IOPV JSON 解析失败", e);
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        if (value.isBlank() || value.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant epochSeconds(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            long value = node.asLong(0);
            return value > 0 ? Instant.ofEpochSecond(value) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public record Page(int total, Map<String, Quote> quotes) {
        private static Page empty() {
            return new Page(0, Map.of());
        }
    }

    public record Quote(String code, BigDecimal iopv, Instant updatedAt, String dataDate) {
    }
}
