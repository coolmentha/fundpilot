package com.fundpilot.backend.market.client;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 东方财富 JS 字面量响应解析器(基于 GraalVM JavaScript 引擎)。
 * <p>三个数据线的 JS 解析方法:{@link #parseNavHistory(String)},
 * {@link #parseFundDict(String)}, {@link #parseIndexKline(String)}。
 * 每个方法构建独立 {@link Context}(线程不安全,每次新开)。
 */
public final class EastmoneyJsParser {

    private EastmoneyJsParser() {
    }

    /**
     * 解析 pingzhongdata.js 提取 {@code Data_netWorthTrend}(单位净值)+
     * {@code Data_ACWorthTrend}(累计净值),按索引位置对齐(Date→nav→accumulatedNav)。
     *
     * @param rawJs pingzhongdata.js 原始响应文本
     * @return 按日期升序的净值快照列表;任一数组为空则返空列表
     */
    public static List<FundNavSnapshot> parseNavHistory(String rawJs) {
        try (Context context = Context.create("js")) {
            context.eval("js", rawJs);

            Value netWorth = context.getBindings("js").getMember("Data_netWorthTrend");
            Value acWorth = context.getBindings("js").getMember("Data_ACWorthTrend");

            if (!netWorth.hasArrayElements() || !acWorth.hasArrayElements()) {
                return List.of();
            }
            int size = (int) netWorth.getArraySize();
            if (size == 0 || (int) acWorth.getArraySize() != size) {
                return List.of();
            }

            List<FundNavSnapshot> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Value nw = netWorth.getArrayElement(i);
                Value aw = acWorth.getArrayElement(i);

                // netWorth 元素是对象 {x: 毫秒戳, y: 单位净值, ...};acWorth 元素是二元数组 [毫秒戳, 累计净值]
                Instant date = Instant.ofEpochMilli(nw.getMember("x").asLong());
                BigDecimal nav = BigDecimal.valueOf(nw.getMember("y").asDouble());
                BigDecimal accumulatedNav = BigDecimal.valueOf(aw.getArrayElement(1).asDouble());
                result.add(new FundNavSnapshot(date, nav, accumulatedNav));
            }
            return List.copyOf(result);
        }
    }

    /**
     * 解析 fundcode_search.js 提取全量基金字典。
     * <p>真实响应是 5 元组数组:{@code [fundCode, 拼音缩写, fundName(中文), 类型描述, 拼音全称]}。
     * 取 {@code [0]} 代码、{@code [2]} 中文名称、{@code [3]} 类型描述(如"混合型-灵活"/"指数型-股票")。
     *
     * @param rawJs fundcode_search.js 原始响应文本
     * @return 全量基金条目列表
     */
    public static List<FundDictEntry> parseFundDict(String rawJs) {
        try (Context context = Context.create("js")) {
            context.eval("js", rawJs);
            Value arr = context.getBindings("js").getMember("r");
            if (!arr.hasArrayElements()) {
                return List.of();
            }
            int size = (int) arr.getArraySize();
            List<FundDictEntry> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Value row = arr.getArrayElement(i);
                result.add(new FundDictEntry(
                        row.getArrayElement(0).asString(),
                        row.getArrayElement(2).asString(),
                        row.getArrayElement(3).asString()
                ));
            }
            return List.copyOf(result);
        }
    }

    /**
     * 解析 push2his.eastmoney.com 指数 K 线 JSON 响应,提取 {@code data.klines} 中的 OHLCV 字符串数组,
     * 每行 CSV 格式 {@code yyyy-MM-dd,open,close,high,low,volume,...}。
     * <p>用 Jackson 解析 JSON(响应是标准 JSON,Jackson 比 GraalVM JS 的 JSON.parse 对大响应更可靠),
     * 仅净值/字典两条线因是 JS 字面量才用 GraalVM。
     *
     * @param rawJson push2his 响应文本(JSON)
     * @return 按日期升序的 K 线柱线集合
     */
    public static IndexKline parseIndexKline(String rawJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(rawJson);
            com.fasterxml.jackson.databind.JsonNode klines = root.path("data").path("klines");
            if (!klines.isArray() || klines.isEmpty()) {
                return new IndexKline(List.of());
            }
            List<IndexKline.Bar> bars = new ArrayList<>(klines.size());
            for (com.fasterxml.jackson.databind.JsonNode csvNode : klines) {
                String csv = csvNode.asText();
                String[] parts = csv.split(",");
                if (parts.length < 6) {
                    continue; // 跳过空行/汇总行等非标准行
                }
                bars.add(new IndexKline.Bar(
                        java.time.LocalDate.parse(parts[0]).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                        new BigDecimal(parts[1]),
                        new BigDecimal(parts[2]),
                        new BigDecimal(parts[3]),
                        new BigDecimal(parts[4]),
                        Long.parseLong(parts[5])
                ));
            }
            return new IndexKline(List.copyOf(bars));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("指数 K 线 JSON 解析失败", e);
        }
    }

    /**
     * 解析 fundgz.1234567.com.cn 盘中估值响应(issue #36)。
     * <p>响应是 JSONP 包裹 {@code jsonpgz({...});},剥外壳后是标准 JSON,含:
     * <ul>
     *   <li>{@code gszzl} 估算涨跌幅(百分比字符串,如 "-4.62")</li>
     *   <li>{@code gztime} 估值时间(如 "2026-06-26 15:00")</li>
     *   <li>{@code jzrq} 基准净值日期(估算所基于的已结算净值日期)</li>
     * </ul>
     * 用 Jackson 解析(标准 JSON)。缺 gszzl 字段或空响应返 null(降级,估值失败不影响主流程)。
     *
     * @param rawJs fundgz 响应文本(JSONP)
     * @return 估值快照;空响应或缺关键字段返 null
     */
    public static FundEstimateSnapshot parseFundGz(String rawJs) {
        if (rawJs == null || rawJs.isBlank()) {
            return null;
        }
        String json = stripJsonp(rawJs);
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(json);
            com.fasterxml.jackson.databind.JsonNode gszzl = root.path("gszzl");
            if (gszzl.isMissingNode() || gszzl.asText().isBlank()) {
                return null; // 缺估算涨跌幅,无法用
            }
            // gszzl 是百分比字符串(如 "-4.62"),除 100 转小数
            BigDecimal estimatedChangePct = new BigDecimal(gszzl.asText())
                    .divide(new BigDecimal("100"), MathContext.DECIMAL64);
            String estimateTime = root.path("gztime").asText(null);
            String baseNavDate = root.path("jzrq").asText(null);
            return new FundEstimateSnapshot(estimatedChangePct, estimateTime, baseNavDate);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("fundgz 盘中估值解析失败", e);
        }
    }

    /** 剥 JSONP 外壳 {@code jsonpgz(...);} 取内层 JSON。 */
    private static String stripJsonp(String raw) {
        int start = raw.indexOf('(');
        int end = raw.lastIndexOf(')');
        if (start < 0 || end < 0 || end <= start) {
            return raw; // 非标准 JSONP,原样交 Jackson 解析(可能抛异常由调用方处理)
        }
        return raw.substring(start + 1, end);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 把 Java 字符串转成 JS 字符串字面量(转义反斜杠和引号),供 {@code JSON.parse(...)} 调用。
     */
    private static String jsStringLiteral(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}