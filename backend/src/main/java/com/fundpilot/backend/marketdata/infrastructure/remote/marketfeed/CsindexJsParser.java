package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 中证指数公司(csindex.com.cn)响应解析器:把 {@code /csindex-home/perf/index-perf} 返回的
 * JSON {@code data[]} 数组解析成 {@link IndexKline} 日线柱。
 * <p>响应结构:
 * <pre>{@code
 * {"code":"200","msg":"Success","data":[
 *   {"tradeDate":"20260105","indexCode":"930713","open":5337.3,"high":5454.83,
 *    "low":5337.3,"close":5451.43,"tradingVol":2.029886206E9,"tradingValue":1365.83,...},
 *   ...
 * ]}
 * }</pre>
 * <p>tradeDate 是 yyyyMMdd 字符串,转 UTC 0 点 Instant(对齐 InstantDateConverter 约定)。
 * tradingVol 单位是股,直接用作 volume(图表成交量只需序列内一致)。
 * <p>空 data(如 399xxx 深交所指数不在中证公司编制范围)抛 {@link IllegalStateException},
 * 让 {@link MarketDataSourceChain} 降级到东方财富。
 * <p>防御性跳过周末 bar:csindex 正常只返交易日,但 startDate 恰逢节假日时首条会复刻下一交易日的 OHLC
 * (已实测),按周末过滤可消除该边界伪影。
 */
public final class CsindexJsParser {

    private static final DateTimeFormatter TRADE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private CsindexJsParser() {
    }

    /**
     * @param rawJson   csindex 响应文本
     * @param indexCode 裸指数代码(仅用于异常信息)
     * @return 按日期升序的日线柱列表
     * @throws IllegalStateException data 为空或解析失败
     */
    public static IndexKline parseIndexKline(String rawJson, String indexCode) {
        JsonNode root;
        try {
            root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawJson);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("csindex 指数 JSON 解析失败: " + indexCode, e);
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            // 非 CSI 编制范围(如 399xxx 深交所指数)返空 data——抛异常让降级链回退东方财富。
            throw new IllegalStateException("csindex 无此指数或无数据: " + indexCode);
        }
        List<IndexKline.Bar> bars = new ArrayList<>(data.size());
        for (JsonNode row : data) {
            String tradeDate = row.path("tradeDate").asText();
            LocalDate d = LocalDate.parse(tradeDate, TRADE_DATE_FMT);
            // 防御性跳过周末(消除 startDate=节假日时首条复刻下一交易日的边界伪影)
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            Instant date = d.atStartOfDay(ZoneOffset.UTC).toInstant();
            bars.add(new IndexKline.Bar(
                    date,
                    row.path("open").decimalValue(),
                    row.path("close").decimalValue(),
                    row.path("high").decimalValue(),
                    row.path("low").decimalValue(),
                    row.path("tradingVol").asLong(0L)
            ));
        }
        if (bars.isEmpty()) {
            throw new IllegalStateException("csindex 解析后无有效日线(全周末?): " + indexCode);
        }
        return new IndexKline(List.copyOf(bars));
    }

    /**
     * 把日 K 聚合成周/月 K(中证接口仅提供日 K)。
     * <ul>
     *   <li>daily:原样返回</li>
     *   <li>weekly:按所在周一分组;open=首日、high=max、low=min、close=末日、volume=sum,date=末日</li>
     *   <li>monthly:按月首分组,同上</li>
     * </ul>
     * 与 {@code KlineService.aggregate} 语义一致(蜡烛绘在周期末)。
     *
     * @param daily   升序日线柱
     * @param period  "daily"/"weekly"/"monthly"(或 d/w/m)
     */
    public static IndexKline aggregate(IndexKline daily, String period) {
        if (period == null || isDaily(period)) {
            return daily;
        }
        boolean weekly = isWeekly(period);
        java.util.Map<LocalDate, List<IndexKline.Bar>> groups = new java.util.LinkedHashMap<>();
        for (IndexKline.Bar b : daily.bars()) {
            LocalDate d = b.date().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate key = weekly ? d.with(DayOfWeek.MONDAY) : d.withDayOfMonth(1);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        List<IndexKline.Bar> out = new ArrayList<>(groups.size());
        for (List<IndexKline.Bar> g : groups.values()) {
            IndexKline.Bar first = g.get(0);
            IndexKline.Bar last = g.get(g.size() - 1);
            BigDecimal high = g.stream().map(IndexKline.Bar::high)
                    .filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
            BigDecimal low = g.stream().map(IndexKline.Bar::low)
                    .filter(java.util.Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
            long vol = g.stream().mapToLong(b -> b.volume()).sum();
            out.add(new IndexKline.Bar(last.date(), first.open(), last.close(), high, low, vol));
        }
        return new IndexKline(List.copyOf(out));
    }

    private static boolean isDaily(String p) {
        return "daily".equalsIgnoreCase(p) || "d".equalsIgnoreCase(p);
    }

    private static boolean isWeekly(String p) {
        return "weekly".equalsIgnoreCase(p) || "w".equalsIgnoreCase(p) || "week".equalsIgnoreCase(p);
    }
}
