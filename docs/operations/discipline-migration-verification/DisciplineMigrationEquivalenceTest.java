/*
 * ============================================================================
 * 归档副本（v0.14.0 B-6 离线等价比对脚本）
 * ============================================================================
 * 原始位置：backend/src/test/java/com/fundpilot/backend/config/DisciplineMigrationEquivalenceTest.java
 * 归档原因：C-8 要求把 B-6 的比对脚本作为「纪律判定迁移已完成」的可复查证据保留在 docs/operations/ 下。
 *
 * 为什么它在 C 段之后不能再编译：
 *   1. 它直接引用 discipline 模块的生产类 —— com.fundpilot.backend.discipline.domain.advice.AdvicePolicy
 *      （AdviceAction、Facts、Market）与 com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy。
 *      C 段（C-4/C-5）会把整个 discipline 包与其测试一并删除，这些类型不复存在。
 *   2. 它所在的测试类本身位于 backend/src/test/... 下，会随 discipline 模块一起从源码树删除。
 *   因此归档件只作为比对口径与结论的可复查凭据保留，不参与构建；C 段之后若要重新运行等价比对，
 *   需要先把 discipline 侧的旧实现从版本历史中取回。
 *
 * 运行方式（迁移完成、discipline 仍在源码树时）：
 *   $env:JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"
 *   .\mvnw.cmd -o test "-Dtest=DisciplineMigrationEquivalenceTest" "-Dsurefire.failIfNoSpecifiedTests=false"
 * 结论与统计数据见同目录的 equivalence-report.md。
 * ============================================================================
 */

package com.fundpilot.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.alerting.application.condition.ConditionEvaluationService;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionEvaluator;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionState;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitPhase;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitPolicy;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdvicePolicy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * v0.14.0 B-6：纪律（discipline）判定迁移到提醒（alerting）之后的离线等价比对。
 *
 * <p>本类只做「判定口径是否等价」的比对，不触碰数据库、不联网：旧侧直接跑真实的
 * {@link AdvicePolicy}，新侧把同一组状态映射为指标数值序列后跑真实的
 * {@link ConditionEvaluator} 与 {@link ConditionEvaluationService}，并按
 * {@code AlertSuggestionService.logicBroken}／{@code trailingStop} 的顺序复现判定。
 *
 * <p>放在 {@code com.fundpilot.backend.config} 是因为该包不是 Spring Modulith 业务模块，
 * 可以同时引用 discipline 与 alerting 两侧（与同包的 {@code DisciplineOwnershipMigrationTest} 同理）。
 */
class DisciplineMigrationEquivalenceTest {

    private static final Instant 交易日 = Instant.parse("2026-09-21T00:00:00Z");

    /** 旧侧逻辑破坏止损的「加仓至今交易日数」，测试里固定为 10（≥ 最短持有 5 日）。 */
    private static final long 加仓至今交易日数 = 10;

    /** 回撤止盈的全参数网格里统一的最大单次卖出比例与冷静期交易日。 */
    private static final BigDecimal 统一单次上限 = new BigDecimal("0.2");

    private static final int 统一冷静期 = 10;

    /** 周线 MACD 的三个窗口参数，与旧 marketdata 写死的 12/26/9 一致。 */
    private static final Map<String, Integer> MACD参数 = Map.of("fast", 12, "slow", 26, "signal", 9);

    // ------------------------------------------------------------------
    // 测试 A：逻辑破坏止损的全状态空间等价
    // ------------------------------------------------------------------

    /**
     * 测试 A：逻辑破坏止损在「产品类型 × 年线位置 × 周线 MACD 状态 × 量能状态 × 持仓状态 × 份额正负」
     * 全状态空间上的等价性。
     *
     * <p><b>状态 → 指标数值序列的映射</b>（映射只保留两者判定真正依赖的语义，不追求数值上与生产计算一致）：
     * <ul>
     *   <li>{@code priceAboveYearLine=false} → {@code PRICE_VS_MA} 序列 {@code [-0.01]}（低于均线，偏离率为负）；
     *       {@code true} → {@code [0.01]}；{@code null} → 空序列（无数据，新旧两侧都按「不满足」处理）。</li>
     *   <li>{@code GREEN_EXPANDING} → 周线 MACD 柱序列 {@code [-0.02, -0.05]}（为负且较上周更小）；
     *       {@code GREEN_SHRINKING} → {@code [-0.05, -0.02]}；{@code RED_EXPANDING} → {@code [0.02, 0.05]}；
     *       {@code RED_SHRINKING} → {@code [0.05, 0.02]}；{@code null} → 空序列。</li>
     *   <li>{@code HIGH_DROP} → {@code VOLUME_DROP} 序列 {@code [1.8]}（量比 1.8 ≥ 1.5）；
     *       {@code NORMAL} → {@code [1.0]}；{@code LOW_STABLE} → {@code [0.4]}；{@code null} → 空序列。</li>
     *   <li>ACTIVE 产品类型：新侧不参与 {@code VOLUME_DROP} 条件，与旧侧 {@code productType == ACTIVE} 豁免一致。</li>
     * </ul>
     *
     * <p>旧侧的口径是「跌破年线 且 周线 MACD 绿柱扩大 且（主动型 或 量能 HIGH_DROP）→ 建议全仓卖」；
     * 新侧由 {@code PRICE_VS_MA BELOW 0} + {@code WEEKLY_MACD_HISTOGRAM BELOW 0} +
     * {@code WEEKLY_MACD_HISTOGRAM DECREASING} 三条条件合取表达，非 ACTIVE 再追加
     * {@code VOLUME_DROP ABOVE 1.5}。
     *
     * <p>为避免旧侧的回撤止盈分支干扰本测试的口径，持仓单位成本固定为 0（旧侧
     * {@code positive(costPerShare)} 前置校验因此直接返回 {@code NO_SELL_TRIGGER}），
     * 于是旧侧唯一的卖出来源就是逻辑破坏止损。持仓份额取 0 与 1000 两档。
     *
     * <p><b>已发现的唯一差异（如实记录，不放宽）</b>：在 {@code 持仓状态=OPEN 且份额为零} 这一组合上，
     * 旧 {@link AdvicePolicy} 命中逻辑破坏止损时不做「持仓份额为正」的前置校验，会返回「卖出 0 份」；
     * 新 {@code AlertSuggestionService.logicBroken} 增加了 {@code positive(holdingShares)} 前置校验，
     * 因此不建议卖出。该组合在会计口径下不可达：{@code Position.reconcile} 只按净份额判定
     * （{@code netShares.signum() > 0 ? OPEN : CLEARED}），故 {@code OPEN} 必然对应正份额。
     * 本测试因而把差异分成「可达组合」与「不可达组合」两桶：可达组合必须为空，不可达组合只允许
     * 出现这 7 条已知差异（ACTIVE 的 4 个量能取值 + 其余 3 个产品类型的 {@code HIGH_DROP}）。
     */
    @Test
    void 逻辑破坏止损在全状态空间上与旧实现等价() {
        DisciplineStrategy 旧策略 = 策略("0.15", "0.06", "0.50", "0.50", "0.2", 统一冷静期);

        int 组合数 = 0;
        int 旧侧命中数 = 0;
        int 新侧命中数 = 0;
        List<String> 可达差异 = new ArrayList<>();
        List<String> 不可达差异 = new ArrayList<>();

        for (AdvicePolicy.ProductType 产品类型 : AdvicePolicy.ProductType.values()) {
            for (Boolean 价格在年线上方 : new Boolean[] {false, true, null}) {
                for (AdvicePolicy.MacdState 周线Macd : 含空Macd状态()) {
                    for (AdvicePolicy.VolumeState 量能状态 : 含空量能状态()) {
                        for (AdvicePolicy.PositionStatus 持仓状态 : AdvicePolicy.PositionStatus.values()) {
                            for (BigDecimal 持仓份额 : List.of(BigDecimal.ZERO, new BigDecimal("1000"))) {
                                组合数++;
                                boolean 旧侧卖出 = 旧侧逻辑破坏止损卖出(旧策略, 产品类型, 持仓状态, 持仓份额,
                                        价格在年线上方, 周线Macd, 量能状态);
                                boolean 新侧卖出 = 新侧逻辑破坏止损命中(持仓状态 == AdvicePolicy.PositionStatus.OPEN,
                                        产品类型.name(), 持仓份额, 年线偏离率序列(价格在年线上方),
                                        周线Macd柱序列(周线Macd), 量能序列(量能状态));
                                if (旧侧卖出) {
                                    旧侧命中数++;
                                }
                                if (新侧卖出) {
                                    新侧命中数++;
                                }
                                if (旧侧卖出 == 新侧卖出) {
                                    continue;
                                }
                                String 条目 = "产品类型=" + 产品类型 + ", 年线上方=" + 价格在年线上方
                                        + ", 周线MACD=" + 周线Macd + ", 量能=" + 量能状态
                                        + ", 持仓状态=" + 持仓状态 + ", 份额=" + 持仓份额
                                        + " → 旧" + (旧侧卖出 ? "卖出" : "不卖")
                                        + " / 新" + (新侧卖出 ? "卖出" : "不卖");
                                if (状态组合可达(持仓状态, 持仓份额)) {
                                    可达差异.add(条目);
                                } else {
                                    不可达差异.add(条目);
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("[测试A] 枚举组合数=" + 组合数 + ", 旧侧命中数=" + 旧侧命中数 + ", 新侧命中数=" + 新侧命中数
                + ", 可达差异数=" + 可达差异.size() + ", 不可达组合差异数=" + 不可达差异.size());
        可达差异.forEach(条目 -> System.out.println("[测试A] 可达差异: " + 条目));
        不可达差异.forEach(条目 -> System.out.println("[测试A] 不可达组合差异: " + 条目));

        assertThat(可达差异).as("会计口径下可达的（持仓状态, 份额）组合上，两侧卖出结论必须逐一相等").isEmpty();
        assertThat(新侧命中数 + 不可达差异.size()).as("旧侧命中数应等于新侧命中数加上不可达组合的已知差异").isEqualTo(旧侧命中数);
        assertThat(不可达差异).as("不可达组合上的差异只允许来自 OPEN + 份额为零的 7 个组合")
                .hasSize(7)
                .allSatisfy(条目 -> assertThat(条目).contains("持仓状态=OPEN, 份额=0").contains("旧卖出 / 新不卖"));
    }

    /**
     * 该（持仓状态, 持仓份额）组合在会计口径下是否可达。
     *
     * <p>{@code Position.reconcile} 按 {@code netShares.signum() > 0 ? OPEN : CLEARED} 判定状态、
     * 无 CONFIRMED 账本时为 {@code EMPTY}，因此 {@code OPEN} 必然对应正份额，{@code EMPTY}/{@code CLEARED}
     * 必然是零份额。
     */
    private static boolean 状态组合可达(AdvicePolicy.PositionStatus 持仓状态, BigDecimal 持仓份额) {
        boolean 份额为正 = 持仓份额.signum() > 0;
        return 持仓状态 == AdvicePolicy.PositionStatus.OPEN ? 份额为正 : !份额为正;
    }

    // ------------------------------------------------------------------
    // 测试 B：回撤止盈的全参数网格等价
    // ------------------------------------------------------------------

    /**
     * 测试 B：回撤止盈在「四档预设参数 × 持仓数值网格 × 周期峰值 × 成熟可赎回份额 × 状态阶段」上的等价性。
     *
     * <p>旧侧用 {@link DisciplineStrategy#rehydrate} 构造带运行期状态的策略，新侧用
     * {@link TakeProfitParams} + {@link SuggestionState} 构造；两侧的阶段、周期峰值、冷静期起始时间
     * 一一对应。参数四档预设：宽基 0.15/0.06/0.5/0.5、行业 0.20/0.08/0.5/0.4、主动 0.15/0.07/0.5/0.5、
     * 混合 0.12/0.05/0.4/0.6，统一 maxSingleSell=0.2、cooldownDays=10。
     *
     * <p>断言：是否卖出相等；卖出时建议份额相等（两者都用 DECIMAL64，理论上精确相等，
     * 出现末位差异时退化为相对误差 ≤ 1e-9 并计数）。
     */
    @Test
    void 回撤止盈在全参数网格上与旧实现等价() {
        List<预设参数> 预设 = List.of(
                new 预设参数("宽基", new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                        new BigDecimal("0.50")),
                new 预设参数("行业", new BigDecimal("0.20"), new BigDecimal("0.08"), new BigDecimal("0.50"),
                        new BigDecimal("0.40")),
                new 预设参数("主动", new BigDecimal("0.15"), new BigDecimal("0.07"), new BigDecimal("0.50"),
                        new BigDecimal("0.50")),
                new 预设参数("混合", new BigDecimal("0.12"), new BigDecimal("0.05"), new BigDecimal("0.40"),
                        new BigDecimal("0.60")));

        List<BigDecimal> 单位成本档 = List.of(BigDecimal.ZERO, new BigDecimal("1.0"));
        List<BigDecimal> 持仓份额档 = List.of(BigDecimal.ZERO, new BigDecimal("1000"));
        List<BigDecimal> 单位净值档 = List.of(new BigDecimal("1.0"), new BigDecimal("1.3"), new BigDecimal("0.8"));
        List<BigDecimal> 累计净值档 = List.of(new BigDecimal("1.0"), new BigDecimal("1.5"), new BigDecimal("1.2"));
        List<BigDecimal> 周期峰值档 = java.util.Arrays.asList(null, new BigDecimal("1.0"), new BigDecimal("1.5"),
                new BigDecimal("1.8"));
        List<BigDecimal> 成熟份额档 = List.of(BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("1000"));

        int 组合数 = 0;
        int 旧侧命中数 = 0;
        int 新侧命中数 = 0;
        int 近似比较次数 = 0;
        List<String> 差异 = new ArrayList<>();

        for (预设参数 参数 : 预设) {
            for (BigDecimal 单位成本 : 单位成本档) {
                for (BigDecimal 持仓份额 : 持仓份额档) {
                    for (BigDecimal 单位净值 : 单位净值档) {
                        for (BigDecimal 累计净值 : 累计净值档) {
                            for (BigDecimal 周期峰值 : 周期峰值档) {
                                for (BigDecimal 成熟份额 : 成熟份额档) {
                                    for (状态场景 场景 : 状态场景.values()) {
                                        组合数++;
                                        String 阶段 = 场景.phase;
                                        Instant 冷静期起始 = 场景.冷静期起始();
                                        BigDecimal 状态峰值 = 场景.使用峰值 ? 周期峰值 : null;

                                        boolean 旧侧冷静期已走完 = 冷静期起始 == null
                                                || 交易日数(冷静期起始, 交易日) >= 参数.cooldownDays();
                                        boolean 新侧冷静期已走完 = 冷静期起始 == null
                                                || 交易日数(冷静期起始, 交易日) >= 参数.cooldownDays();
                                        assertThat(新侧冷静期已走完).isEqualTo(旧侧冷静期已走完);

                                        DisciplineStrategy 旧策略 = 旧侧回撤止盈策略(参数, 阶段, 状态峰值, 冷静期起始);
                                        AdvicePolicy.Result 旧结果 = 旧策略评审(旧策略, 单位成本, 持仓份额, 单位净值,
                                                累计净值, 成熟份额, 旧侧冷静期已走完);

                                        SuggestionState 新状态 = SuggestionState.rehydrate(1L, 1L, 2L, 3L,
                                                TakeProfitPhase.valueOf(阶段), 状态峰值 == null ? null : 交易日,
                                                状态峰值, 冷静期起始);
                                        BigDecimal 新份额 = 新侧回撤止盈建议份额(参数, 新状态, 单位成本, 持仓份额,
                                                单位净值, 累计净值, 成熟份额);

                                        boolean 旧侧卖出 = 旧结果.action() == AdviceAction.SELL;
                                        boolean 新侧卖出 = 新份额 != null;
                                        if (旧侧卖出) {
                                            旧侧命中数++;
                                        }
                                        if (新侧卖出) {
                                            新侧命中数++;
                                        }
                                        if (旧侧卖出 != 新侧卖出) {
                                            差异.add("预设=" + 参数.名称() + ", 阶段=" + 阶段 + ", 成本=" + 单位成本
                                                    + ", 份额=" + 持仓份额 + ", 单位净值=" + 单位净值
                                                    + ", 累计净值=" + 累计净值 + ", 峰值=" + 状态峰值
                                                    + ", 成熟份额=" + 成熟份额
                                                    + " → 旧" + (旧侧卖出 ? "卖出" : "不卖")
                                                    + " / 新" + (新侧卖出 ? "卖出" : "不卖"));
                                        } else if (旧侧卖出) {
                                            int 比较 = 份额比较(旧结果.suggestedValue(), 新份额);
                                            if (比较 > 0) {
                                                差异.add("预设=" + 参数.名称() + ", 阶段=" + 阶段 + ", 成本=" + 单位成本
                                                        + ", 份额=" + 持仓份额 + ", 单位净值=" + 单位净值
                                                        + ", 累计净值=" + 累计净值 + ", 峰值=" + 状态峰值
                                                        + ", 成熟份额=" + 成熟份额
                                                        + " → 建议份额 旧=" + 旧结果.suggestedValue()
                                                        + " / 新=" + 新份额);
                                            } else if (比较 < 0) {
                                                近似比较次数++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("[测试B] 参数网格组合数=" + 组合数 + ", 旧侧命中数=" + 旧侧命中数 + ", 新侧命中数=" + 新侧命中数
                + ", 差异数=" + 差异.size() + ", 需要近似比较的建议份额次数=" + 近似比较次数);
        差异.forEach(条目 -> System.out.println("[测试B] 差异: " + 条目));
        assertThat(差异).as("回撤止盈在新旧两侧的卖出结论与建议份额必须逐一相等").isEmpty();
        assertThat(新侧命中数).as("两侧命中数也应相等").isEqualTo(旧侧命中数);
        assertThat(近似比较次数).as("两侧都为 DECIMAL64，建议份额不应出现末位差异").isZero();
    }

    // ------------------------------------------------------------------
    // 测试 C：生产历史状态组合回放
    // ------------------------------------------------------------------

    /**
     * 测试 C：用生产库导出的 {@code market_indicator_snapshot join fund_product} 快照逐行回放，
     * 比较旧侧逻辑破坏止损判定与新侧映射后判定的结论。
     *
     * <p>回放文件默认取 {@code ../docs/operations/discipline-migration-verification/market-indicator-snapshot-export.csv}
     * （surefire 的工作目录是 {@code backend/}），可用系统属性 {@code fundpilot.b6.snapshot} 覆盖。
     *
     * <p>本回放验证的是<b>判定规则映射与 ACTIVE/null 豁免</b>在真实历史状态组合上的一致性；原始净值/量能序列
     * → 状态枚举的推导属于 A 段指标计算与既有 marketdata 测试的范围，本测试不重复验证。
     */
    @Test
    void 生产历史状态组合回放差异为空() throws IOException {
        Path 快照文件 = Path.of(System.getProperty("fundpilot.b6.snapshot",
                "../docs/operations/discipline-migration-verification/market-indicator-snapshot-export.csv"));
        assertThat(Files.exists(快照文件)).as("本地生产快照导出必须存在: " + 快照文件.toAbsolutePath()).isTrue();

        List<String> 行 = Files.readAllLines(快照文件, StandardCharsets.UTF_8);
        assertThat(行).as("快照导出至少要有表头与一行数据").hasSizeGreaterThan(1);

        DisciplineStrategy 旧策略 = 策略("0.15", "0.06", "0.50", "0.50", "0.2", 统一冷静期);

        int 回放行数 = 0;
        int 旧侧命中数 = 0;
        int 新侧命中数 = 0;
        Map<String, Integer> 旧侧按产品类型命中 = new LinkedHashMap<>();
        Map<String, Integer> 新侧按产品类型命中 = new LinkedHashMap<>();
        List<String> 差异 = new ArrayList<>();

        for (int i = 1; i < 行.size(); i++) {
            String 原始行 = 行.get(i);
            if (原始行 == null || 原始行.isBlank() || !Character.isDigit(原始行.charAt(0))) {
                // 跳过空行与 psql 导出尾部的 "(1581 rows)" 摘要行，只回放真实数据行
                continue;
            }
            String[] 列 = 原始行.split(",", -1);
            assertThat(列).as("第 " + (i + 1) + " 行的列数应为 8: " + 原始行).hasSize(8);
            String 业务日期 = 列[0].trim();
            String 基金代码 = 列[1].trim();
            String 产品类型 = 列[3].trim();
            Boolean 价格在年线上方 = 解析布尔(列[4]);
            AdvicePolicy.MacdState 周线Macd = 解析Macd状态(列[5]);
            AdvicePolicy.VolumeState 量能状态 = 解析量能状态(列[6]);

            回放行数++;
            boolean 旧侧卖出 = 旧侧逻辑破坏止损卖出(旧策略, AdvicePolicy.ProductType.valueOf(产品类型),
                    AdvicePolicy.PositionStatus.OPEN, new BigDecimal("1000"), 价格在年线上方, 周线Macd, 量能状态);
            boolean 新侧卖出 = 新侧逻辑破坏止损命中(true, 产品类型, new BigDecimal("1000"),
                    年线偏离率序列(价格在年线上方), 周线Macd柱序列(周线Macd), 量能序列(量能状态));
            if (旧侧卖出) {
                旧侧命中数++;
                旧侧按产品类型命中.merge(产品类型, 1, Integer::sum);
            }
            if (新侧卖出) {
                新侧命中数++;
                新侧按产品类型命中.merge(产品类型, 1, Integer::sum);
            }
            if (旧侧卖出 != 新侧卖出) {
                差异.add(业务日期 + " 基金" + 基金代码 + "(" + 产品类型 + ") 年线上方=" + 价格在年线上方
                        + " 周线MACD=" + 周线Macd + " 量能=" + 量能状态
                        + " → 旧" + (旧侧卖出 ? "卖出" : "不卖") + " / 新" + (新侧卖出 ? "卖出" : "不卖"));
            }
        }

        System.out.println("[测试C] 回放的(基金,交易日)对数=" + 回放行数 + ", 旧侧命中数=" + 旧侧命中数
                + ", 新侧命中数=" + 新侧命中数 + ", 差异数=" + 差异.size());
        System.out.println("[测试C] 旧侧按产品类型命中数=" + 旧侧按产品类型命中);
        System.out.println("[测试C] 新侧按产品类型命中数=" + 新侧按产品类型命中);
        差异.forEach(条目 -> System.out.println("[测试C] 差异: " + 条目));
        assertThat(差异).as("生产历史状态组合逐行回放的结论必须完全一致").isEmpty();
        assertThat(新侧命中数).as("两侧命中数也应相等").isEqualTo(旧侧命中数);
        assertThat(回放行数).as("本地快照导出的数据行数").isEqualTo(1581);
    }

    // ------------------------------------------------------------------
    // 新侧判定的复现（与 AlertSuggestionService 同顺序）
    // ------------------------------------------------------------------

    /**
     * 复现 {@code AlertSuggestionService.logicBroken}：先逐条求值三条件、再求值非主动型基金的量能条件，
     * 全部满足后要求持仓份额为正才给出卖出。
     *
     * <p>状态机部分在新建状态下必然处于 {@code ACCUMULATING}（非 {@code TRIGGERED}），因此沿用
     * 「命中即卖出」的判定；这不影响结论，因为逻辑破坏止损的判定与阶段无关。
     */
    private static boolean 新侧逻辑破坏止损命中(boolean 在持, String 产品类型, BigDecimal 持仓份额,
                                            List<BigDecimal> 年线偏离率, List<BigDecimal> 周线Macd柱,
                                            List<BigDecimal> 量能比) {
        if (!在持) {
            return false;
        }
        List<ConditionEvaluationService.Row> 条件行 = new ArrayList<>();
        for (AlertCondition 条件 : 逻辑破坏止损条件组().conditions()) {
            List<BigDecimal> 取值 = switch (条件.indicator()) {
                case PRICE_VS_MA -> 年线偏离率;
                case WEEKLY_MACD_HISTOGRAM -> 周线Macd柱;
                default -> throw new IllegalStateException("逻辑破坏止损条件组出现意外指标: " + 条件.indicator());
            };
            条件行.add(条件取值行(条件, 取值));
        }
        ConditionEvaluationService.Row 量能行 = "ACTIVE".equals(产品类型)
                ? null
                : 条件取值行(AlertCondition.of(IndicatorCode.VOLUME_DROP, ConditionRelation.ABOVE), 量能比);
        if (!ConditionEvaluationService.satisfied(条件行) || (量能行 != null && !量能行.satisfied())) {
            return false;
        }
        return TakeProfitPolicy.positive(持仓份额);
    }

    /** 复现 {@code AlertSuggestionService.trailingStop}：返回 null 表示不建议卖出，否则为建议卖出份额。 */
    private static BigDecimal 新侧回撤止盈建议份额(预设参数 参数, SuggestionState 状态, BigDecimal 单位成本,
                                             BigDecimal 持仓份额, BigDecimal 单位净值, BigDecimal 累计净值,
                                             BigDecimal 成熟份额) {
        TakeProfitParams 止盈参数 = 参数.toParams();

        BigDecimal 浮盈 = null;
        BigDecimal 收益率 = null;
        if (TakeProfitPolicy.positive(单位成本) && TakeProfitPolicy.positive(持仓份额)
                && TakeProfitPolicy.positive(单位净值)) {
            BigDecimal 持仓成本 = TakeProfitPolicy.holdingCost(单位成本, 持仓份额);
            浮盈 = TakeProfitPolicy.floatingProfit(单位成本, 持仓份额, 单位净值);
            收益率 = TakeProfitPolicy.overallReturn(浮盈, 持仓成本);
        }
        boolean 冷静期已走完 = 状态.cooldownStartedAt() == null
                || 交易日数(状态.cooldownStartedAt(), 交易日) >= 止盈参数.cooldownDays();
        boolean 可触发 = 状态.prepareTakeProfit(收益率, 累计净值, 止盈参数.activation(), 交易日, 冷静期已走完);

        if (!可触发 || !TakeProfitPolicy.positive(成熟份额)) {
            return null;
        }
        BigDecimal 回撤 = TakeProfitPolicy.pullback(状态.cyclePeakNav(), 累计净值);
        if (回撤 == null || 回撤.compareTo(止盈参数.pullback()) < 0) {
            return null;
        }
        BigDecimal 建议份额 = TakeProfitPolicy.suggestedShares(浮盈, 持仓份额, 单位净值, 止盈参数, 成熟份额);
        return TakeProfitPolicy.positive(建议份额) ? 建议份额 : null;
    }

    /** 逻辑破坏止损的规范条件组：跌破年线 + 周线 MACD 绿柱 + 绿柱扩大。 */
    private static ConditionGroup 逻辑破坏止损条件组() {
        return ConditionGroup.allOf(List.of(
                new AlertCondition(IndicatorCode.PRICE_VS_MA, Map.of("window", 250), ConditionRelation.BELOW,
                        BigDecimal.ZERO),
                new AlertCondition(IndicatorCode.WEEKLY_MACD_HISTOGRAM, MACD参数, ConditionRelation.BELOW,
                        BigDecimal.ZERO),
                new AlertCondition(IndicatorCode.WEEKLY_MACD_HISTOGRAM, MACD参数, ConditionRelation.DECREASING,
                        null)));
    }

    private static ConditionEvaluationService.Row 条件取值行(AlertCondition 条件, List<BigDecimal> 取值) {
        return new ConditionEvaluationService.Row(条件, 取值.isEmpty() ? null : 取值.getLast(),
                ConditionEvaluator.satisfied(条件, 取值));
    }

    // ------------------------------------------------------------------
    // 旧侧判定的驱动
    // ------------------------------------------------------------------

    /** 旧侧逻辑破坏止损是否给出卖出；持仓成本固定为 0，以使回撤止盈分支不会产生干扰性卖出。 */
    private static boolean 旧侧逻辑破坏止损卖出(DisciplineStrategy 旧策略, AdvicePolicy.ProductType 产品类型,
                                          AdvicePolicy.PositionStatus 持仓状态, BigDecimal 持仓份额,
                                          Boolean 价格在年线上方, AdvicePolicy.MacdState 周线Macd,
                                          AdvicePolicy.VolumeState 量能状态) {
        AdvicePolicy.Facts 事实 = new AdvicePolicy.Facts(产品类型, 持仓状态, BigDecimal.ZERO, 持仓份额,
                new AdvicePolicy.Market(价格在年线上方, 周线Macd, 量能状态), new BigDecimal("1.0"),
                new BigDecimal("1.0"), BigDecimal.ZERO, 交易日);
        return new AdvicePolicy().evaluate(旧策略, 事实, 加仓至今交易日数, true).action() == AdviceAction.SELL;
    }

    /** 旧侧回撤止盈：市场条件设为「价格在年线上方」以使逻辑破坏止损不成立，从而只走止盈分支。 */
    private static AdvicePolicy.Result 旧策略评审(DisciplineStrategy 旧策略, BigDecimal 单位成本, BigDecimal 持仓份额,
                                               BigDecimal 单位净值, BigDecimal 累计净值, BigDecimal 成熟份额,
                                               boolean 冷静期已走完) {
        AdvicePolicy.Facts 事实 = new AdvicePolicy.Facts(AdvicePolicy.ProductType.ETF,
                AdvicePolicy.PositionStatus.OPEN, 单位成本, 持仓份额,
                new AdvicePolicy.Market(true, null, null), 单位净值, 累计净值, 成熟份额, 交易日);
        return new AdvicePolicy().evaluate(旧策略, 事实, 加仓至今交易日数, 冷静期已走完);
    }

    private static DisciplineStrategy 旧侧回撤止盈策略(预设参数 参数, String 阶段, BigDecimal 周期峰值,
                                                 Instant 冷静期起始) {
        return DisciplineStrategy.rehydrate(1L, 2L, 3L, "EFFECTIVE", 参数.activation(), 参数.pullback(),
                参数.harvest(), 参数.minimumHolding(), 参数.maxSingleSell(), 参数.cooldownDays(), null, null, true,
                阶段, 周期峰值 == null ? null : 交易日, 周期峰值, null, 冷静期起始);
    }

    private static DisciplineStrategy 策略(String activation, String pullback, String harvest, String minimumHolding,
                                          String maxSingleSell, int cooldownDays) {
        DisciplineStrategy 策略 = DisciplineStrategy.create(1L, 2L, new DisciplineStrategy.Input(
                new BigDecimal(activation), new BigDecimal(pullback), new BigDecimal(harvest),
                new BigDecimal(minimumHolding), new BigDecimal(maxSingleSell), cooldownDays));
        策略.activate();
        return 策略;
    }

    // ------------------------------------------------------------------
    // 状态 → 指标数值序列的映射（测试 A 与测试 C 共用）
    // ------------------------------------------------------------------

    /** 年线位置 → 净值与均线偏离率序列：低于均线为负、高于均线为正、无数据为空序列。 */
    private static List<BigDecimal> 年线偏离率序列(Boolean 价格在年线上方) {
        if (价格在年线上方 == null) {
            return List.of();
        }
        return List.of(价格在年线上方 ? new BigDecimal("0.01") : new BigDecimal("-0.01"));
    }

    /** 周线 MACD 状态 → 柱高序列（两期）：绿柱为负、红柱为正，放大/缩小由后一期相对前一期体现。 */
    private static List<BigDecimal> 周线Macd柱序列(AdvicePolicy.MacdState 状态) {
        if (状态 == null) {
            return List.of();
        }
        return switch (状态) {
            case GREEN_EXPANDING -> List.of(new BigDecimal("-0.02"), new BigDecimal("-0.05"));
            case GREEN_SHRINKING -> List.of(new BigDecimal("-0.05"), new BigDecimal("-0.02"));
            case RED_EXPANDING -> List.of(new BigDecimal("0.02"), new BigDecimal("0.05"));
            case RED_SHRINKING -> List.of(new BigDecimal("0.05"), new BigDecimal("0.02"));
        };
    }

    /** 量能状态 → 放量下跌量比序列：HIGH_DROP 达 1.5 倍以上，其余不足。 */
    private static List<BigDecimal> 量能序列(AdvicePolicy.VolumeState 状态) {
        if (状态 == null) {
            return List.of();
        }
        return switch (状态) {
            case HIGH_DROP -> List.of(new BigDecimal("1.8"));
            case NORMAL -> List.of(new BigDecimal("1.0"));
            case LOW_STABLE -> List.of(new BigDecimal("0.4"));
        };
    }

    private static List<AdvicePolicy.MacdState> 含空Macd状态() {
        List<AdvicePolicy.MacdState> 全部 = new ArrayList<>(java.util.Arrays.asList(AdvicePolicy.MacdState.values()));
        全部.add(null);
        return 全部;
    }

    private static List<AdvicePolicy.VolumeState> 含空量能状态() {
        List<AdvicePolicy.VolumeState> 全部 = new ArrayList<>(
                java.util.Arrays.asList(AdvicePolicy.VolumeState.values()));
        全部.add(null);
        return 全部;
    }

    private static Boolean 解析布尔(String 原文) {
        String 文本 = 原文 == null ? "" : 原文.trim();
        if (文本.isEmpty()) {
            return null;
        }
        return Boolean.valueOf(文本);
    }

    private static AdvicePolicy.MacdState 解析Macd状态(String 原文) {
        String 文本 = 原文 == null ? "" : 原文.trim();
        return 文本.isEmpty() ? null : AdvicePolicy.MacdState.valueOf(文本);
    }

    private static AdvicePolicy.VolumeState 解析量能状态(String 原文) {
        String 文本 = 原文 == null ? "" : 原文.trim();
        return 文本.isEmpty() ? null : AdvicePolicy.VolumeState.valueOf(文本);
    }

    // ------------------------------------------------------------------
    // 比较与辅助
    // ------------------------------------------------------------------

    /** 0 表示精确相等；-1 表示末位差异但相对误差 ≤ 1e-9；1 表示不相等。 */
    private static int 份额比较(BigDecimal 旧值, BigDecimal 新值) {
        if (旧值.compareTo(新值) == 0) {
            return 0;
        }
        BigDecimal 容差 = 新值.abs().multiply(new BigDecimal("1e-9"));
        return 旧值.subtract(新值).abs().compareTo(容差) <= 0 ? -1 : 1;
    }

    /** 两个日期之间（不含起点、含终点）的交易日数；测试里自然日即交易日。 */
    private static long 交易日数(Instant 起点, Instant 终点) {
        return Duration.between(起点, 终点).toDays();
    }

    /** 四档预设参数。 */
    private record 预设参数(String 名称, BigDecimal activation, BigDecimal pullback, BigDecimal harvest,
                          BigDecimal minimumHolding) {

        BigDecimal maxSingleSell() {
            return 统一单次上限;
        }

        int cooldownDays() {
            return 统一冷静期;
        }

        TakeProfitParams toParams() {
            return new TakeProfitParams(activation, pullback, harvest, minimumHolding, maxSingleSell(),
                    cooldownDays());
        }
    }

    /** 测试 B 的状态阶段场景：阶段、是否使用周期峰值、冷静期起始时间。 */
    private enum 状态场景 {
        ACCUMULATING("ACCUMULATING", false, null),
        ARMED("ARMED", true, null),
        TRIGGERED("TRIGGERED", true, null),
        COOLDOWN_冷静期内("COOLDOWN", false, "期内"),
        COOLDOWN_冷静期外("COOLDOWN", false, "期外");

        private final String phase;
        private final boolean 使用峰值;
        private final String 冷静期标记;

        状态场景(String phase, boolean 使用峰值, String 冷静期标记) {
            this.phase = phase;
            this.使用峰值 = 使用峰值;
            this.冷静期标记 = 冷静期标记;
        }

        Instant 冷静期起始() {
            if (冷静期标记 == null) {
                return null;
            }
            return "期内".equals(冷静期标记) ? 交易日 : 交易日.minus(Duration.ofDays(统一冷静期));
        }
    }
}
