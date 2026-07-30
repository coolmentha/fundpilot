package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.MathContext;
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

    /** 解析同花顺盘中估值分钟线，取最后一个有效点计算相对基准净值涨跌幅。 */
    public static FundEstimateSnapshot parseFundEstimate(String raw) {
        FundIntradayChart chart = parseFundIntradayChart(raw);
        return chart == null ? null : parseFundEstimateFrom(chart);
    }

    public static FundEstimateSnapshot parseFundEstimateFrom(FundIntradayChart chart) {
        if (chart.points().isEmpty()) {
            return null;
        }
        BigDecimal latestNav = chart.points().getLast().nav();
        BigDecimal changePct = latestNav.subtract(chart.baseNav()).divide(chart.baseNav(), MathContext.DECIMAL64);
        return new FundEstimateSnapshot(changePct, chart.estimateDate() + " " + chart.points().getLast().time(),
                chart.baseNavDate());
    }

    /** 解析同花顺盘中分钟估值线。无有效点时返回 null，单点仅可用于估值快照、不可绘制曲线。 */
    public static FundIntradayChart parseFundIntradayChart(String raw) {
        String payload = ScriptPayloadExtractor.assignedValueByPrefix(raw, "vm_fd_");
        if (payload == null) {
            return null;
        }
        try {
            String[] sides = payload.split("\\|", 2);
            String baseNavDate = sides[0].split(";", 2)[0];
            String[] estimate = sides[1].split("~", 3);
            BigDecimal baseNav = new BigDecimal(estimate[1]);
            List<FundIntradayChart.Point> points = new ArrayList<>();
            for (String csv : estimate[2].split(";")) {
                String[] candidate = csv.split(",");
                if (candidate.length >= 2 && candidate[0].matches("\\d{4}") && !candidate[1].isBlank()) {
                    String time = candidate[0].substring(0, 2) + ":" + candidate[0].substring(2, 4);
                    points.add(new FundIntradayChart.Point(time, new BigDecimal(candidate[1])));
                }
            }
            if (points.isEmpty()) {
                return null;
            }
            return new FundIntradayChart(estimate[0], baseNavDate, baseNav, List.copyOf(points));
        } catch (RuntimeException e) {
            throw new IllegalStateException("同花顺盘中估值解析失败", e);
        }
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

    /** 解析同花顺大盘涨跌停分钟统计的最新一个有效点。 */
    public static MarketLimitCounts parseMarketLimitCounts(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode data = MAPPER.readTree(raw).path("zdt_data");
            JsonNode times = data.path("zd_time");
            JsonNode limitUps = data.path("ztzs");
            JsonNode limitDowns = data.path("dtzs");
            if (!times.isArray() || times.isEmpty() || !limitUps.isArray() || !limitDowns.isArray()
                    || times.size() != limitUps.size() || times.size() != limitDowns.size()) {
                return null;
            }
            int last = times.size() - 1;
            if (!times.get(last).asText("").matches("(?:09|10|11|13|14|15):[0-5]\\d")) {
                return null;
            }
            Integer limitUp = nonNegativeInt(limitUps.get(last));
            Integer limitDown = nonNegativeInt(limitDowns.get(last));
            return limitUp == null || limitDown == null ? null : new MarketLimitCounts(limitUp, limitDown);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("同花顺涨跌停统计 JSON 解析失败", e);
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

    private static Integer nonNegativeInt(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 0 ? node.intValue() : null;
    }

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ObjectMapper MAPPER = new ObjectMapper();
}
