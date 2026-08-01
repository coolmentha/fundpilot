package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 东方财富 JS 字面量响应解析器。
 * <p>三个数据线的 JS 解析方法:{@link #parseNavHistory(String)},
 * {@link #parseFundDict(String)}, {@link #parseIndexKline(String)}。
 * JS 变量只做受限结构提取，再交给 Jackson 解析，不执行远端脚本。
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
        String netWorthJson = ScriptPayloadExtractor.assignedValue(rawJs, "Data_netWorthTrend");
        String accumulatedJson = ScriptPayloadExtractor.assignedValue(rawJs, "Data_ACWorthTrend");
        if (netWorthJson == null || accumulatedJson == null) {
            return List.of();
        }
        try {
            JsonNode netWorth = MAPPER.readTree(netWorthJson);
            JsonNode accumulated = MAPPER.readTree(accumulatedJson);
            if (!netWorth.isArray() || netWorth.isEmpty() || !accumulated.isArray()) {
                return List.of();
            }

            Map<Long, BigDecimal> accumulatedByTimestamp = new HashMap<>();
            for (JsonNode row : accumulated) {
                if (row.isArray() && row.size() >= 2 && row.get(0).canConvertToLong() && row.get(1).isNumber()) {
                    accumulatedByTimestamp.put(row.get(0).longValue(), row.get(1).decimalValue());
                }
            }

            List<FundNavSnapshot> result = new ArrayList<>(netWorth.size());
            for (JsonNode row : netWorth) {
                JsonNode timestampNode = row.path("x");
                JsonNode navNode = row.path("y");
                if (!timestampNode.canConvertToLong() || !navNode.isNumber()) {
                    continue;
                }
                long timestamp = timestampNode.longValue();
                BigDecimal accumulatedNav = accumulatedByTimestamp.get(timestamp);
                if (accumulatedNav != null) {
                    Instant date = ChinaTradingDate.toUtcDate(Instant.ofEpochMilli(timestamp));
                    result.add(new FundNavSnapshot(date, navNode.decimalValue(), accumulatedNav));
                }
            }
            return List.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("基金净值 JS 解析失败", e);
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
        String json = ScriptPayloadExtractor.assignedValue(rawJs, "r");
        if (json == null) {
            return List.of();
        }
        try {
            JsonNode rows = MAPPER.readTree(json);
            if (!rows.isArray()) {
                return List.of();
            }
            List<FundDictEntry> result = new ArrayList<>(rows.size());
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 4) {
                    continue;
                }
                result.add(new FundDictEntry(
                        row.get(0).asText(),
                        row.get(2).asText(),
                        row.get(3).asText()
                ));
            }
            return List.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("基金字典 JS 解析失败", e);
        }
    }

    /**
     * 解析 push2his.eastmoney.com 指数 K 线 JSON 响应,提取 {@code data.klines} 中的 OHLCV 字符串数组,
     * 每行 CSV 格式 {@code yyyy-MM-dd,open,close,high,low,volume,...}。
     * <p>用 Jackson 解析 JSON；净值/字典的变量赋值响应同样先做结构提取再交 Jackson。
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

    /**
     * 解析 push2 ulist.np 批量指数实时行情 JSON。
     * <p>响应结构 {@code data.diff[]} 数组,每个元素含 f2(价格 ÷100)、f3(涨跌幅 ÷10000,返小数)、
     * f4(涨跌额 ÷100)、f6(成交额 元)、f12(代码)、f14(名称)。
     * f12 只含代码不含市场前缀(如 "000001"),secid 由调用方按请求顺序映射回填。
     *
     * @param rawJson    ulist.np 响应文本
     * @param secidOrder 请求时的 secid 顺序(如 ["1.000001","1.000300"]),用于按 f12 回填完整 secid
     * @return 指数实时快照列表;data 为空或解析失败返空列表(降级,不抛异常)
     */
    public static List<IndexRealtimeSnapshot> parseIndexRealtime(String rawJson, List<String> secidOrder) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(rawJson);
            com.fasterxml.jackson.databind.JsonNode diff = root.path("data").path("diff");
            if (!diff.isArray() || diff.isEmpty()) {
                return List.of();
            }
            // f12(如 "000001") → secid(如 "1.000001") 的反查表。
            // f13(市场前缀,如 "1"/"0") 能区分沪深同后缀代码(如 1.000001 上证指数 vs 0.000001 平安银行),
            // 优先按完整 "市场.代码" 匹配;上游缺 f13 的旧响应按代码后缀退化匹配(同后缀冲突由调用方去重)。
            java.util.Map<String, String> secidByFullCode = new java.util.HashMap<>();
            java.util.Map<String, String> secidBySuffix = new java.util.HashMap<>();
            for (String secid : secidOrder) {
                secidByFullCode.put(secid, secid);
                int dot = secid.indexOf('.');
                if (dot > 0 && dot < secid.length() - 1) {
                    secidBySuffix.put(secid.substring(dot + 1), secid);
                }
            }
            List<IndexRealtimeSnapshot> result = new ArrayList<>(diff.size());
            for (com.fasterxml.jackson.databind.JsonNode node : diff) {
                String code = textOrNull(node, "f12");
                String market = textOrNull(node, "f13");
                String secid;
                if (code == null) {
                    secid = null;
                } else if (market != null) {
                    secid = secidByFullCode.getOrDefault(market + "." + code, code);
                } else {
                    secid = secidBySuffix.getOrDefault(code, code);
                }
                BigDecimal price = scaledDecimal(node, "f2", 100);
                // f3 涨跌幅:东方财富返百分比值×100(如 37 表 0.37%),÷10000 还原成小数(0.0037)
                // 契约(IndexRealtimeSnapshot.changePct)要求小数,前端 signedPercent 再 ×100 显示。
                // 历史 bug:曾 ÷100 返 0.37(百分比),前端 ×100 显示成 37%。
                BigDecimal changePct = scaledDecimal(node, "f3", 10000);
                BigDecimal changeAmount = scaledDecimal(node, "f4", 100);
                BigDecimal turnover = decimalOrNull(node, "f6");
                result.add(new IndexRealtimeSnapshot(secid, textOrNull(node, "f14"),
                        price, changeAmount, changePct, turnover));
            }
            return List.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("指数实时行情 JSON 解析失败", e);
        }
    }

    /**
     * 解析沪深京股票市场上涨、下跌家数。
     *
     * <p>调用方传入上证、深证、北证三个固定市场 secid。只有全部市场均存在且
     * f104(上涨家数)、f105(下跌家数)完整时才返回汇总，避免发布部分市场数据。
     *
     * @param rawJson      ulist.np 响应文本
     * @param marketSecids 必须完整参与汇总的市场 secid
     * @return 市场宽度快照；响应为空、市场缺失或字段缺失时返回 null
     */
    public static MarketBreadthSnapshot parseMarketBreadth(String rawJson, List<String> marketSecids) {
        if (rawJson == null || rawJson.isBlank() || marketSecids == null || marketSecids.isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(rawJson);
            com.fasterxml.jackson.databind.JsonNode diff = root.path("data").path("diff");
            if (!diff.isArray() || diff.isEmpty()) {
                return null;
            }

            java.util.Map<String, com.fasterxml.jackson.databind.JsonNode> nodesByCode = new java.util.HashMap<>();
            for (com.fasterxml.jackson.databind.JsonNode node : diff) {
                String code = textOrNull(node, "f12");
                if (code != null) {
                    nodesByCode.put(code, node);
                }
            }

            int risingCount = 0;
            int fallingCount = 0;
            for (String secid : marketSecids) {
                int dot = secid.indexOf('.');
                if (dot < 0 || dot == secid.length() - 1) {
                    return null;
                }
                com.fasterxml.jackson.databind.JsonNode node = nodesByCode.get(secid.substring(dot + 1));
                if (node == null) {
                    return null;
                }
                Integer rising = integerOrNull(node, "f104");
                Integer falling = integerOrNull(node, "f105");
                if (rising == null || falling == null) {
                    return null;
                }
                risingCount = Math.addExact(risingCount, rising);
                fallingCount = Math.addExact(fallingCount, falling);
            }
            return new MarketBreadthSnapshot(risingCount, fallingCount);
        } catch (java.io.IOException | ArithmeticException e) {
            throw new IllegalStateException("市场宽度 JSON 解析失败", e);
        }
    }

    /**
     * 解析 push2 clist 行业板块涨跌 + 资金流向 JSON。
     * <p>响应结构 {@code data.diff[]} 数组,每个元素含 f3(涨跌幅 ÷10000,返小数)、f6(成交额)、
     * f12(板块代码)、f14(板块名称)、f62(主力净流入 元,可缺失)。
     *
     * @param rawJson clist 响应文本
     * @return 板块快照列表(按东方财富返回顺序);data 为空或解析失败返空列表
     */
    public static List<SectorSnapshot> parseSectorList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(rawJson);
            com.fasterxml.jackson.databind.JsonNode diff = root.path("data").path("diff");
            if (!diff.isArray() || diff.isEmpty()) {
                return List.of();
            }
            List<SectorSnapshot> result = new ArrayList<>(diff.size());
            for (com.fasterxml.jackson.databind.JsonNode node : diff) {
                // f3 涨跌幅 ÷10000 返小数(契约要求小数,见 SectorSnapshot.changePct 注释)
                result.add(new SectorSnapshot(
                        textOrNull(node, "f12"),
                        textOrNull(node, "f14"),
                        scaledDecimal(node, "f3", 10000),
                        decimalOrNull(node, "f6"),
                        decimalOrNull(node, "f62")));
            }
            return List.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("行业板块 JSON 解析失败", e);
        }
    }

    /**
     * 解析 push2 kamt.rtmin 北向资金实时净流入 JSON。
     * <p>响应结构 {@code data.s2n[]} 字符串数组,每条 CSV 格式
     * {@code HH:MM,沪股通净流入,沪股通余额,深股通净流入,深股通余额,北向合计净流入}。
     * 取最后一条作为最新值,北向合计 = CSV 第 5 列(索引 5)。时间为当日 + "HH:MM"。
     *
     * @param rawJson kamt.rtmin 响应文本
     * @return 北向资金快照;s2n 为空或解析失败返 null(降级)
     */
    public static MoneyFlowSnapshot parseNorthbound(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(rawJson);
            com.fasterxml.jackson.databind.JsonNode s2n = root.path("data").path("s2n");
            if (!s2n.isArray() || s2n.isEmpty()) {
                return null;
            }
            String last = s2n.get(s2n.size() - 1).asText();
            String[] parts = last.split(",");
            if (parts.length < 6) {
                return null;
            }
            BigDecimal northboundNet = decimalFromText(parts[5]);
            if (northboundNet == null || northboundNet.compareTo(BigDecimal.ZERO) == 0) {
                // 0 视为无效:非交易时段(周末/盘前/盘后)东方财富 kamt.rtmin 返 0 占位,
                // 显示「北向净流入 0」会误导用户以为当日无资金动向。返 null 让前端显示
                // 「暂无资金流向数据」。交易时段真实北向恰好 0 极罕见,当无数据优于显示 0。
                return null;
            }
            // 时间:当日 UTC 日期 + "HH:MM"(北向资金是 A 股盘中数据,用 UTC 当日即可,前端按本地时区展示)
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
            String[] hm = parts[0].split(":");
            Instant snapshotTime = today.atTime(
                    Integer.parseInt(hm[0]), Integer.parseInt(hm[1]))
                    .atZone(java.time.ZoneOffset.UTC).toInstant();
            return new MoneyFlowSnapshot(northboundNet, snapshotTime);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("北向资金 JSON 解析失败", e);
        }
    }

    /** 取 JSON 节点的字符串值,缺失返 null。 */
    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    /** 取 JSON 节点的数值字段并 ÷scale 还原(scale=100:如 f2=404364 → 4043.64;scale=10000:如 f3=37 → 0.0037)。缺失返 null。 */
    private static BigDecimal scaledDecimal(com.fasterxml.jackson.databind.JsonNode node, String field, int scale) {
        com.fasterxml.jackson.databind.JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.decimalValue().divide(new BigDecimal(scale), MathContext.DECIMAL64);
    }

    /** 取 JSON 节点的数值字段(原值不缩放)。缺失返 null。 */
    private static BigDecimal decimalOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.decimalValue();
    }

    /** 取非负整数字段，缺失、非整数或越界时返回 null。 */
    private static Integer integerOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode child = node.path(field);
        if (!child.isIntegralNumber() || !child.canConvertToInt()) {
            return null;
        }
        int value = child.intValue();
        return value >= 0 ? value : null;
    }

    /** 从 CSV 文本片段解析 BigDecimal,空或非法返 null。 */
    private static BigDecimal decimalFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
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

}
