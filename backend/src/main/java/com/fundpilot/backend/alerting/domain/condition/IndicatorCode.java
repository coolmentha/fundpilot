package com.fundpilot.backend.alerting.domain.condition;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 提醒条件可用的指标种类（首批五组维度：周线 MACD 状态、年线与区间位置、量能状态、持仓收益与回撤、
 * PE 估值）。
 *
 * <p>每个指标把口径归一为一条标量序列，条件只描述「该标量与阈值或前一个取值的关系」。指标的可调参数在
 * 此声明，前端据此渲染输入框、服务端据此校验并兜底默认值。
 *
 * <p>行情类指标的取值由 MarketData 按需计算，指标码必须与 {@code IndicatorComputeQueryHandler} 的
 * 支持清单保持一致（有契约测试兜底），本枚举只负责元数据与校验。
 */
public enum IndicatorCode {

    DAILY_CHANGE("当日涨跌幅", "最新净值相对上一交易日的涨跌幅，0.05 表示上涨 5%",
            IndicatorSource.FUND_FACT, List.of(),
            new BigDecimal("-1"), new BigDecimal("1"),
            IndicatorRelation.of(ConditionRelation.ABOVE, new BigDecimal("0.05")),
            IndicatorRelation.of(ConditionRelation.BELOW, new BigDecimal("-0.05"))),

    HOLDING_RETURN("持仓收益率", "当前持仓的浮动收益率，0.15 表示盈利 15%",
            IndicatorSource.FUND_FACT, List.of(),
            new BigDecimal("-1"), new BigDecimal("5"),
            IndicatorRelation.of(ConditionRelation.ABOVE, new BigDecimal("0.15")),
            IndicatorRelation.of(ConditionRelation.BELOW, new BigDecimal("-0.10")),
            IndicatorRelation.of(ConditionRelation.INCREASING),
            IndicatorRelation.of(ConditionRelation.DECREASING)),

    PRICE_VS_MA("净值与均线偏离率", "累计净值相对近 N 个交易日均线的偏离率，负数表示在均线下方（年线即 250 日）",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("window", "均线窗口（交易日）", 250, 5, 250)),
            new BigDecimal("-1"), new BigDecimal("5"),
            IndicatorRelation.of(ConditionRelation.ABOVE, BigDecimal.ZERO),
            IndicatorRelation.of(ConditionRelation.BELOW, BigDecimal.ZERO),
            IndicatorRelation.named(ConditionRelation.CROSS_ABOVE, "上穿均线", null),
            IndicatorRelation.named(ConditionRelation.CROSS_BELOW, "下穿均线", null)),

    MA("净值均线", "近 N 个交易日累计净值的简单均线数值，可与净值水平比较或用「上升/下降」判断趋势",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("window", "均线窗口（交易日）", 250, 5, 250)),
            BigDecimal.ZERO, new BigDecimal("100000"),
            IndicatorRelation.of(ConditionRelation.ABOVE),
            IndicatorRelation.of(ConditionRelation.BELOW),
            IndicatorRelation.named(ConditionRelation.INCREASING, "均线上升", null),
            IndicatorRelation.named(ConditionRelation.DECREASING, "均线下降", null)),

    MA_CROSS("均线快慢线差值", "快线均线减慢线均线：大于 0 为多头排列，由负转正即金叉",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("fast", "快线窗口（交易日）", 20, 2, 120),
                    new IndicatorParameter("slow", "慢线窗口（交易日）", 50, 3, 250)),
            new BigDecimal("-100"), new BigDecimal("100"),
            IndicatorRelation.named(ConditionRelation.ABOVE, "多头排列", BigDecimal.ZERO),
            IndicatorRelation.named(ConditionRelation.BELOW, "空头排列", BigDecimal.ZERO),
            IndicatorRelation.named(ConditionRelation.CROSS_ABOVE, "金叉", null),
            IndicatorRelation.named(ConditionRelation.CROSS_BELOW, "死叉", null)),

    NAV_RANGE_POSITION("净值区间位置", "累计净值在近 N 个交易日区间中的位置：0 为区间最低、1 为区间最高",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("window", "区间窗口（交易日）", 250, 5, 250)),
            BigDecimal.ZERO, BigDecimal.ONE,
            IndicatorRelation.named(ConditionRelation.ABOVE, "接近区间新高", new BigDecimal("0.9")),
            IndicatorRelation.named(ConditionRelation.BELOW, "接近区间新低", new BigDecimal("0.1"))),

    NAV_DRAWDOWN("净值回撤", "累计净值距近 N 个交易日峰值的回撤比例，0.08 表示回撤 8%",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("window", "回撤观察窗口（交易日）", 250, 5, 250)),
            BigDecimal.ZERO, BigDecimal.ONE,
            IndicatorRelation.of(ConditionRelation.ABOVE, new BigDecimal("0.05")),
            IndicatorRelation.of(ConditionRelation.BELOW, BigDecimal.ZERO),
            IndicatorRelation.of(ConditionRelation.INCREASING),
            IndicatorRelation.of(ConditionRelation.DECREASING)),

    VOLUME_RATIO("基准指数量能比", "基准指数最新成交量与近 N 日均量之比，1.5 表示放量 50%",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("window", "均量窗口（交易日）", 20, 3, 120)),
            BigDecimal.ZERO, new BigDecimal("10"),
            IndicatorRelation.named(ConditionRelation.ABOVE, "放量", new BigDecimal("1.5")),
            IndicatorRelation.named(ConditionRelation.BELOW, "缩量", new BigDecimal("0.5")),
            IndicatorRelation.named(ConditionRelation.INCREASING, "量能放大", null),
            IndicatorRelation.named(ConditionRelation.DECREASING, "量能缩小", null)),

    VOLUME_DROP("基准指数量能比（放量下跌）", "基准指数收阴且成交量高于近 N 日均量时的量比，收阳时为 0；"
            + "口径与旧纪律的量能状态 HIGH_DROP 一致",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("window", "均量窗口（交易日）", 20, 3, 120)),
            BigDecimal.ZERO, new BigDecimal("10"),
            IndicatorRelation.named(ConditionRelation.ABOVE, "放量下跌", new BigDecimal("1.5"))),

    WEEKLY_MACD_HISTOGRAM("周线 MACD 柱高", "周线 MACD 柱：正为红柱、负为绿柱；柱高较上周的变化即放大或缩小",
            IndicatorSource.MARKET_DATA,
            List.of(new IndicatorParameter("fast", "快线周期（周）", 12, 2, 60),
                    new IndicatorParameter("slow", "慢线周期（周）", 26, 3, 120),
                    new IndicatorParameter("signal", "信号线周期（周）", 9, 2, 60)),
            new BigDecimal("-100"), new BigDecimal("100"),
            IndicatorRelation.named(ConditionRelation.ABOVE, "红柱", BigDecimal.ZERO),
            IndicatorRelation.named(ConditionRelation.BELOW, "绿柱", BigDecimal.ZERO),
            IndicatorRelation.named(ConditionRelation.CROSS_ABOVE, "金叉", null),
            IndicatorRelation.named(ConditionRelation.CROSS_BELOW, "死叉", null),
            IndicatorRelation.named(ConditionRelation.INCREASING, "柱较上周放大", null),
            IndicatorRelation.named(ConditionRelation.DECREASING, "柱较上周缩小", null)),

    INDEX_PE("指数市盈率", "基金基准指数的最新市盈率（PE）",
            IndicatorSource.MARKET_DATA, List.of(),
            BigDecimal.ZERO, new BigDecimal("1000"),
            IndicatorRelation.of(ConditionRelation.ABOVE, new BigDecimal("30")),
            IndicatorRelation.of(ConditionRelation.BELOW, new BigDecimal("10"))),

    INDEX_PE_PERCENTILE("指数 PE 历史分位", "最新 PE 在近 5 年历史样本中的分位：0 为历史最低、1 为历史最高",
            IndicatorSource.MARKET_DATA, List.of(),
            BigDecimal.ZERO, BigDecimal.ONE,
            IndicatorRelation.of(ConditionRelation.ABOVE, new BigDecimal("0.8")),
            IndicatorRelation.of(ConditionRelation.BELOW, new BigDecimal("0.2")));

    private final String label;
    private final String description;
    private final IndicatorSource source;
    private final List<IndicatorParameter> parameters;
    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private final List<IndicatorRelation> relations;

    IndicatorCode(String label, String description, IndicatorSource source, List<IndicatorParameter> parameters,
                  BigDecimal minimum, BigDecimal maximum, IndicatorRelation... relations) {
        this.label = label;
        this.description = description;
        this.source = source;
        this.parameters = List.copyOf(parameters);
        this.minimum = minimum;
        this.maximum = maximum;
        this.relations = List.of(relations);
        requireMetadataConsistent();
    }

    /** 指标码，与 MarketData 的支持清单一致。 */
    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public IndicatorSource source() {
        return source;
    }

    public List<IndicatorParameter> parameters() {
        return parameters;
    }

    /** 用户可填阈值的下界（含）。 */
    public BigDecimal minimum() {
        return minimum;
    }

    /** 用户可填阈值的上界（含）。 */
    public BigDecimal maximum() {
        return maximum;
    }

    public List<IndicatorRelation> relations() {
        return relations;
    }

    public boolean supports(ConditionRelation relation) {
        return relations.stream().anyMatch(candidate -> candidate.relation() == relation);
    }

    /** 取该指标下某个关系的声明；指标不支持该关系时抛出。 */
    public IndicatorRelation relation(ConditionRelation relation) {
        return relations.stream().filter(candidate -> candidate.relation() == relation).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        label + "不支持「" + relation.alias() + "」条件"));
    }

    public Optional<IndicatorParameter> parameter(String name) {
        return parameters.stream().filter(candidate -> candidate.name().equals(name)).findFirst();
    }

    /** 校验参数之间的相互约束（如均线快线窗口必须短于慢线窗口）。 */
    void requireConsistentParams(Map<String, Integer> params) {
        switch (this) {
            case MA_CROSS, WEEKLY_MACD_HISTOGRAM -> {
                if (params.get("fast") >= params.get("slow")) {
                    throw new IllegalArgumentException(label + "的快线周期必须小于慢线周期");
                }
            }
            default -> {
                // 其余指标无参数间约束
            }
        }
    }

    public static Optional<IndicatorCode> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (IndicatorCode candidate : values()) {
            if (candidate.name().equals(normalized)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** 解析指标码；未知指标抛出，供存储与接口层的字段解析使用。 */
    public static IndicatorCode of(String code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("不支持的提醒指标: " + code));
    }

    private void requireMetadataConsistent() {
        Set<String> parameterNames = new LinkedHashSet<>();
        for (IndicatorParameter parameter : parameters) {
            if (!parameterNames.add(parameter.name())) {
                throw new IllegalStateException("指标参数名重复: " + name() + "." + parameter.name());
            }
        }
        Set<ConditionRelation> declared = new LinkedHashSet<>();
        for (IndicatorRelation relation : relations) {
            if (!declared.add(relation.relation())) {
                throw new IllegalStateException("指标关系重复: " + name() + "." + relation.relation());
            }
            BigDecimal defaultThreshold = relation.effectiveDefaultThreshold();
            if (defaultThreshold != null
                    && (defaultThreshold.compareTo(minimum) < 0 || defaultThreshold.compareTo(maximum) > 0)) {
                throw new IllegalStateException("指标关系默认阈值超出取值范围: " + name() + "." + relation.relation());
            }
        }
    }
}