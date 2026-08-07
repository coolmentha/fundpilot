package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 解析 AKShare {@code fund_etf_spot_ths} 返回的 JSONP。 */
public final class ThsEtfSpotParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ThsEtfSpotParser() {
    }

    /**
     * 只取最近确认单位净值 {@code newnet} 和净值日期 {@code newdate}，不把该日增长率当作盘中估值。
     */
    public static Map<String, BaseNav> parse(String raw) {
        String payload = ScriptPayloadExtractor.wrappedValue(raw);
        if (payload == null) {
            return Map.of();
        }
        try {
            JsonNode data = MAPPER.readTree(payload).path("data").path("data");
            if (!data.isObject()) {
                return Map.of();
            }
            Map<String, BaseNav> result = new HashMap<>();
            Iterator<JsonNode> rows = data.elements();
            while (rows.hasNext()) {
                JsonNode row = rows.next();
                String code = row.path("code").asText("");
                BigDecimal nav = decimal(row.path("newnet"));
                String navDate = row.path("newdate").asText("");
                if (code.matches("\\d{6}") && nav != null && nav.signum() > 0
                        && navDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    result.put(code, new BaseNav(nav, navDate));
                }
            }
            return Map.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("同花顺 ETF 最近净值 JSON 解析失败", e);
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

    public record BaseNav(BigDecimal nav, String navDate) {
    }
}
