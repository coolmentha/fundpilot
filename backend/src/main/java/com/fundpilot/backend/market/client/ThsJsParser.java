package com.fundpilot.backend.market.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同花顺公开基金与指数响应解析器。
 *
 * @see ThsClient
 */
public final class ThsJsParser {

    private ThsJsParser() {
    }

    /**
     * 解析单位净值和累计净值两个变量赋值响应，并按 yyyyMMdd 日期关联。
     */
    public static List<FundNavSnapshot> parseNavHistory(String unitRaw, String accumulatedRaw) {
        Map<String, BigDecimal> unit = parseDatedValues(unitRaw, "dwjz_");
        Map<String, BigDecimal> accumulated = parseDatedValues(accumulatedRaw, "ljjz_");
        List<FundNavSnapshot> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : unit.entrySet()) {
            BigDecimal accumulatedNav = accumulated.get(entry.getKey());
            if (accumulatedNav != null) {
                result.add(new FundNavSnapshot(
                        LocalDate.parse(entry.getKey(), YYYYMMDD).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        entry.getValue(), accumulatedNav));
            }
        }
        result.sort(Comparator.comparing(FundNavSnapshot::navDate));
        return List.copyOf(result);
    }

    /**
     * 解析同花顺基金数据中心的 g({...}) JSONP 基金字典。
     */
    public static List<FundDictEntry> parseFundDict(String raw) {
        String json = ScriptPayloadExtractor.wrappedValue(raw);
        if (json == null) {
            return List.of();
        }
        try {
            JsonNode data = MAPPER.readTree(json).path("data").path("data");
            if (!data.isObject()) {
                return List.of();
            }
            List<FundDictEntry> result = new ArrayList<>();
            data.elements().forEachRemaining(node -> {
                String code = node.path("code").asText("");
                String name = node.path("name").asText("");
                if (!code.isBlank() && !name.isBlank()) {
                    result.add(new FundDictEntry(code, name, node.path("typename").asText("")));
                }
            });
            result.sort(Comparator.comparing(FundDictEntry::fundCode));
            return List.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("同花顺基金字典解析失败", e);
        }
    }

    /**
     * 解析 d.10jqka callback({...}) 最近日线响应。
     */
    public static IndexKline parseIndexKline(String raw) {
        String json = ScriptPayloadExtractor.wrappedValue(raw);
        if (json == null) {
            return new IndexKline(List.of());
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            String data = root.path("data").asText("");
            if (root.path("total").asInt(0) == 0 || data.isBlank()) {
                return new IndexKline(List.of());
            }
            List<IndexKline.Bar> bars = new ArrayList<>();
            for (String csv : data.split(";")) {
                String[] fields = csv.split(",");
                if (fields.length < 6) {
                    continue;
                }
                bars.add(new IndexKline.Bar(
                        LocalDate.parse(fields[0], YYYYMMDD).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        new BigDecimal(fields[1]), new BigDecimal(fields[4]),
                        new BigDecimal(fields[2]), new BigDecimal(fields[3]),
                        new BigDecimal(fields[5]).longValue()));
            }
            bars.sort(Comparator.comparing(IndexKline.Bar::date));
            return new IndexKline(List.copyOf(bars));
        } catch (java.io.IOException | NumberFormatException e) {
            throw new IllegalStateException("同花顺指数 K 线解析失败", e);
        }
    }

    private static Map<String, BigDecimal> parseDatedValues(String raw, String variablePrefix) {
        String json = ScriptPayloadExtractor.assignedValueByPrefix(raw, variablePrefix);
        if (json == null) {
            return Map.of();
        }
        try {
            JsonNode rows = MAPPER.readTree(json);
            if (!rows.isArray()) {
                return Map.of();
            }
            Map<String, BigDecimal> result = new HashMap<>();
            for (JsonNode row : rows) {
                if (row.isArray() && row.size() >= 2) {
                    String date = row.get(0).asText();
                    String value = row.get(1).asText();
                    if (!date.isBlank() && !value.isBlank()) {
                        result.put(date, new BigDecimal(value));
                    }
                }
            }
            return result;
        } catch (java.io.IOException | NumberFormatException e) {
            throw new IllegalStateException("同花顺基金净值解析失败", e);
        }
    }

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ObjectMapper MAPPER = new ObjectMapper();
}
