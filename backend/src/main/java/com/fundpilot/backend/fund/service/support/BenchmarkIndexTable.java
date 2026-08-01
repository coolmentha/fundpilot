package com.fundpilot.backend.fund.service.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 指数关键词 → 业绩比较基准指数代码映射(issue #8 §benchmarkIndexCode 处理)。
 * <p>覆盖宽基/主流指数(.SH/.SZ)与行业主题指数(.CSI → secid 2.,如人工智能/半导体/光伏等)。
 * 代码格式 {@code XXXXXX.SH/SZ/CSI}(人类可读、与 CONTEXT.md 一致)。
 * {@code MarketDataFetchService} 调 {@link EastmoneyClient#fetchIndexKline} 前会把此格式转成
 * 东方财富 secid 格式 {@code 1.000300}。
 * <p>用 {@link LinkedHashMap} 保序,长关键词优先匹配(如「沪深300」先于「沪深」),避免短词误命中。
 */
public final class BenchmarkIndexTable {

    /** 关键词 → 指数代码(按长度降序排,保证长词优先)。 */
    private static final Map<String, String> TABLE = buildTable();

    /** 宽基/主流指数关键词(对应基金类型 {@code BROAD_BASE},issue #144)。 */
    private static final Set<String> BROAD_BASE_KEYWORDS = Set.of(
            "中证1000", "中证500", "沪深300", "科创50", "上证50", "创业板");

    private BenchmarkIndexTable() {
    }

    /**
     * 在基金名称里查找首个命中的指数关键词,返回对应指数代码。
     *
     * @param fundName 基金名称(可含前后缀、份额类别等)
     * @return 命中的指数代码(如 {@code "000300.SH"});未命中或入参为空返回 {@link Optional#empty()}
     */
    public static Optional<String> lookup(String fundName) {
        if (fundName == null || fundName.isBlank()) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> entry : TABLE.entrySet()) {
            if (fundName.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * 判断基金名称是否命中宽基指数关键词(issue #144)。
     * <p>行业主题关键词({@code .CSI} 及部分 .SZ,如半导体/光伏/白酒等)命中时返回 {@code false},
     * 供分类器把行业指数基金判为 SECTOR 而非 BROAD_BASE。
     */
    public static boolean isBroadBase(String fundName) {
        if (fundName == null || fundName.isBlank()) {
            return false;
        }
        return BROAD_BASE_KEYWORDS.stream().anyMatch(fundName::contains);
    }

    private static Map<String, String> buildTable() {
        // 长关键词优先(LinkedHashMap 保插入顺序,迭代时按此序匹配)
        Map<String, String> table = new LinkedHashMap<>();
        // 宽基/主流指数(.SH/.SZ)
        table.put("中证1000", "000852.SH");
        table.put("中证500", "000905.SH");
        table.put("沪深300", "000300.SH");
        table.put("科创50", "000688.SH");
        table.put("上证50", "000016.SH");
        table.put("创业板", "399006.SZ");
        // 行业主题指数(.CSI → secid 2.,已逐一核实东方财富可拉 K 线)
        table.put("科创创业50", "931643.CSI");
        table.put("中证机器人", "H30590.CSI");
        table.put("机器人", "H30590.CSI");
        table.put("中证5G通信主题", "931079.CSI");
        table.put("5G通信", "931079.CSI");
        table.put("细分有色金属", "000811.CSI");
        table.put("有色金属", "000811.CSI");
        table.put("人工智能", "930713.CSI");
        table.put("半导体", "931865.CSI");
        table.put("新能源车", "930997.CSI");
        table.put("中证新能源", "399808.SZ");
        table.put("新能源", "399808.SZ");
        table.put("国证绿色电力", "399438.SZ");
        table.put("绿色电力", "399438.SZ");
        table.put("光伏", "931151.CSI");
        table.put("白酒", "930622.CSI");
        table.put("家电", "930697.CSI");
        table.put("旅游", "930633.CSI");
        return table;
    }
}
