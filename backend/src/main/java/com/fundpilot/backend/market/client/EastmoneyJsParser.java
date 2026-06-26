package com.fundpilot.backend.market.client;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
            for (int i = 0; i < size; i++) {
                Value nw = netWorth.getArrayElement(i);
                Value aw = acWorth.getArrayElement(i);

                LocalDate date = LocalDate.parse(String.valueOf(nw.getMember("x").asLong()), fmt);
                BigDecimal nav = BigDecimal.valueOf(nw.getMember("y").asDouble());
                BigDecimal accumulatedNav = BigDecimal.valueOf(aw.getMember("y").asDouble());
                result.add(new FundNavSnapshot(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), nav, accumulatedNav));
            }
            return List.copyOf(result);
        }
    }

    /**
     * 解析 fundcode_search.js 提取全量基金字典(数组的数组,内层 [fundCode, fundName, rawType, ...])。
     * 取前三个字段为 {@link FundDictEntry},rawName 供 #8 启发式分类用。
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
                        row.getArrayElement(1).asString(),
                        row.getArrayElement(2).asString()
                ));
            }
            return List.copyOf(result);
        }
    }

    /**
     * 解析 push2his.eastmoney.com 指数 K 线 JSON 响应,提取 {@code data.klines} 中的 OHLCV 字符串数组,
     * 每行 CSV 格式 {@code yyyy-MM-dd,open,close,high,low,volume,...}。
     * 用 GraalVM JS 的 {@code JSON.parse}(JSON 是合法 JS 表达式),与其它 parseXxx 风格一致。
     *
     * @param rawJson push2his 响应文本(JSON)
     * @return 按日期升序的 K 线柱线集合
     */
    public static IndexKline parseIndexKline(String rawJson) {
        try (Context context = Context.create("js")) {
            Value parsed = context.eval("js", "JSON.parse(" + jsStringLiteral(rawJson) + ")");
            Value klines = parsed.getMember("data").getMember("klines");
            if (!klines.hasArrayElements() || klines.getArraySize() == 0) {
                return new IndexKline(List.of());
            }
            int size = (int) klines.getArraySize();
            List<IndexKline.Bar> bars = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String csv = klines.getArrayElement(i).asString();
                String[] parts = csv.split(",");
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
        }
    }

    /**
     * 把 Java 字符串转成 JS 字符串字面量(转义反斜杠和引号),供 {@code JSON.parse(...)} 调用。
     */
    private static String jsStringLiteral(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}