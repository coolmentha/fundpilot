package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 定投移动止盈回测模拟器单测(issue #57):以回测模拟器为主 seam 端到端验证策略行为(PRD Testing Decisions)。
 * <p>逐个 tracer bullet 增量推进:每月扣款 → 不卖(未启动)→ 启动+止盈卖出 → 底仓/冷却/重置 等。
 */
class DcaTakeProfitSimulatorTest {

    /** tracer bullet 1:每月最后交易日扣款产生 INVEST,金额 = 每期定投金额;净值平稳时无卖出,收益为 0。 */
    @Test
    void 每月最后交易日扣款产生INVEST_金额正确_净值平稳收益为零() {
        // 三个 月末,净值平稳 1.0:每月扣 1000,共 3000 份,期末市值 3000,收益 0
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("1.0"));
        List<Instant> dates = List.of(
                Instant.parse("2025-01-31T00:00:00Z"),
                Instant.parse("2025-02-28T00:00:00Z"),
                Instant.parse("2025-03-31T00:00:00Z"));

        DcaTakeProfitResult result = DcaTakeProfitSimulator.simulate(
                nav, dates, new BigDecimal("1000"), TakeProfitParams.broadDefaults());

        List<DcaTakeProfitResult.SimTrade> invests = result.trades().stream()
                .filter(t -> "INVEST".equals(t.source())).toList();
        assertThat(invests).hasSize(3);
        assertThat(invests).allSatisfy(t -> {
            assertThat(t.amount()).isEqualByComparingTo(new BigDecimal("1000"));
            assertThat(t.shares()).isEqualByComparingTo(new BigDecimal("1000"));
        });
        assertThat(result.strategyReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.dailyValues()).hasSize(3);
    }

    /** tracer bullet 2:High 只增不减——上涨时上移,回调时保持。引擎逐日推进状态,roundHigh = max(市值序列)。 */
    @Test
    void High只增不减_上涨上移_回调保持() {
        TakeProfitParams params = TakeProfitParams.broadDefaults();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        // 持仓 100 份:净值 1.0→1.5(涨)→1.2(回调)
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100"); // 成本 1.0/份

        // day0 nav=1.0,市值 100,High=100
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.0"), params).newState();
        assertThat(state.roundHigh()).isEqualByComparingTo(new BigDecimal("100"));

        // day1 nav=1.5,市值 150,High 上移到 150
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.5"), params).newState();
        assertThat(state.roundHigh()).isEqualByComparingTo(new BigDecimal("150"));

        // day2 nav=1.2,市值 120,回调 — High 保持 150 不降
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.2"), params).newState();
        assertThat(state.roundHigh()).isEqualByComparingTo(new BigDecimal("150"));
    }

    /**
     * tracer bullet 3:达启动门槛(50%)+ 连续 2 日跌破止盈线 → 卖当前持仓 20%。
     * <p>场景(宽基):100 份 × 成本 1.0 = 投入 100。
     * <ul>
     *   <li>nav 1.0(市值 100,yield 0%)→ 未启动</li>
     *   <li>nav 1.6(市值 160,yield 60% ≥ 50%)→ 启动,High=160,peakYield=0.60,档(50~80%)→回撤 15%,止盈线=160×0.85=136;160>136 未跌破</li>
     * </ul>
     * 之后连跌 2 日跌破 136:nav 1.3(市值 130<136,跌破第1日,不卖)→ nav 1.3(市值 130<136,连续第2日,卖 20 份)。
     */
    @Test
    void 达启动门槛并连续2日跌破止盈线_卖当前持仓20percent() {
        TakeProfitParams params = TakeProfitParams.broadDefaults();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.0"), params).newState();
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState();
        assertThat(state.activated()).isTrue();
        assertThat(state.roundHigh()).isEqualByComparingTo(new BigDecimal("160"));

        // 跌破第1日:市值 130 < 止盈线 136 → 不卖
        TrailingStopEngine.Step day1 = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.3"), params);
        assertThat(day1.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
        state = day1.newState();
        assertThat(state.prevDayBelowLine()).isTrue();

        // 连续第2日跌破 → 卖 20% = 20 份
        TrailingStopEngine.Step day2 = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.3"), params);
        assertThat(day2.decision()).isEqualTo(TrailingStopEngine.Decision.SELL);
        assertThat(day2.sellShares()).isEqualByComparingTo(new BigDecimal("20"));
    }

    /** tracer bullet 4:未达启动门槛(收益 30% < 50%)→ 即使跌破也不卖,继续定投。 */
    @Test
    void 未达启动门槛_跌破也不卖() {
        TakeProfitParams params = TakeProfitParams.broadDefaults();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        // nav 1.3 → 市值 130,yield 30% < 50%,未启动;再跌两日 nav 1.0(市值 100)
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.3"), params).newState();
        assertThat(state.activated()).isFalse();
        TrailingStopEngine.Step day1 = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.0"), params);
        assertThat(day1.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
        TrailingStopEngine.Step day2 = TrailingStopEngine.evaluate(day1.newState(), pos(shares, invested), new BigDecimal("1.0"), params);
        assertThat(day2.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
    }

    /**
     * tracer bullet 5:止盈线只上移不降 —— peakYield 升档时目标线更低,但止盈线被旧值钉住不降。
     * <p>100份×成本1=投入100。nav 1.6(市值160,yield60%→档50-80% ratio15%,目标线160×0.85=136,止盈线136);
     * nav 1.8(市值180,yield80%→档80-150% ratio18%,目标线180×0.82=147.6,止盈线上移到147.6);
     * nav 回 1.65(市值165,yield65%→档50-80% ratio15%,目标线165×0.85=140.25 < 147.6)→ 止盈线应保持 147.6 不降。
     */
    @Test
    void 止盈线只上移不降_升档后回落档_止盈线保持最高值() {
        TakeProfitParams params = TakeProfitParams.broadDefaults();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState();
        assertThat(state.roundStopLine()).isEqualByComparingTo(new BigDecimal("136.0"));
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.8"), params).newState();
        assertThat(state.roundStopLine()).isCloseTo(new BigDecimal("147.6"), within(new BigDecimal("0.01")));
        // 回档:目标线更低,止盈线钉住 147.6
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.65"), params).newState();
        assertThat(state.roundStopLine()).isCloseTo(new BigDecimal("147.6"), within(new BigDecimal("0.01")));
    }

    /** tracer bullet 6:只跌破 1 日、第 2 日回升 → 不卖出。 */
    @Test
    void 单日跌破第2日回升_不卖() {
        TakeProfitParams params = TakeProfitParams.broadDefaults();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState(); // 启动
        // 跌破1日
        TrailingStopEngine.Step day1 = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.0"), params);
        assertThat(day1.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
        // 第2日回升到 1.6(未跌破)→ 不卖,prevDayBelowLine 复位
        TrailingStopEngine.Step day2 = TrailingStopEngine.evaluate(day1.newState(), pos(shares, invested), new BigDecimal("1.6"), params);
        assertThat(day2.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
        assertThat(day2.newState().prevDayBelowLine()).isFalse();
    }

    /**
     * tracer bullet 7:累计卖达 60%(底仓 40%)后,再跌破也不卖。
     * <p>用短 cooldown=2 便于连续触发:持仓100、累计买入100、已卖60份(60%)→ 达底仓上限,连2日跌破不卖。
     */
    @Test
    void 累计卖达底仓上限_再跌破也不卖() {
        TakeProfitParams params = shortCooldown(); // cooldown=2,卖率0.20,floor0.40
        // 累计已买入 100 份,已卖出 60 份(60%),当前持仓 40 份 → 已达底仓上限
        TrailingStopEngine.Position nearFloor = new TrailingStopEngine.Position(
                new BigDecimal("40"), new BigDecimal("100"), new BigDecimal("60"),
                new BigDecimal("100"), BigDecimal.ZERO);
        // 构造已启动状态 + 连2日跌破:nav 先涨到 1.6 启动并定 High,再连跌
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        // 用满仓 100 份启动到 High=160
        BigDecimal full = new BigDecimal("100");
        TrailingStopEngine.State started = TrailingStopEngine.evaluate(state,
                new TrailingStopEngine.Position(full, full, new BigDecimal("40"), new BigDecimal("100"), BigDecimal.ZERO),
                new BigDecimal("1.6"), params).newState();
        assertThat(started.activated()).isTrue();
        // 切到近底仓持仓,连2日跌破:day1 不卖(前日未跌破),day2 底仓已满 → 仍不卖
        TrailingStopEngine.Step day1 = TrailingStopEngine.evaluate(started, nearFloor, new BigDecimal("1.0"), params);
        assertThat(day1.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
        TrailingStopEngine.Step day2 = TrailingStopEngine.evaluate(day1.newState(), nearFloor, new BigDecimal("1.0"), params);
        assertThat(day2.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
    }

    /**
     * tracer bullet 8:卖出后冷却期内连2日跌破也不再触发。
     * <p>用极短 cooldown=2 但构造"卖出后仅 1 天"不足以过冷却。cooldown=3:卖出后 daysSinceLastSell 归零,
     * 次日 day1=1、day2=2 都 < 3 → 即使连2日跌破也不卖(冷却内)。
     */
    @Test
    void 卖出后冷却期内_连2日跌破也不触发() {
        TakeProfitParams params = new TakeProfitParams(
                new BigDecimal("0.50"),
                TakeProfitParams.broadDefaults().pullbackTiers(),
                new BigDecimal("0.20"), new BigDecimal("0.40"), 3); // cooldown=3
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        // 启动 + 连2日跌破 → 卖(卖出时 daysSinceLastSell 已 ≥ cooldown)
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState();
        TrailingStopEngine.Step day1 = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.0"), params);
        TrailingStopEngine.Step day2 = TrailingStopEngine.evaluate(day1.newState(), pos(shares, invested), new BigDecimal("1.0"), params);
        assertThat(day2.decision()).isEqualTo(TrailingStopEngine.Decision.SELL); // 首次卖出(冷却已过)
        // 卖出后状态冷却归零;后续连2日跌破但冷却未过(每交易日+1,第2日=2<3)
        TrailingStopEngine.State afterSell = day2.newState();
        TrailingStopEngine.Step s1 = TrailingStopEngine.evaluate(afterSell, pos(new BigDecimal("80"), invested), new BigDecimal("1.0"), params);
        assertThat(s1.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
        TrailingStopEngine.Step s2 = TrailingStopEngine.evaluate(s1.newState(), pos(new BigDecimal("80"), invested), new BigDecimal("1.0"), params);
        assertThat(s2.decision()).isEqualTo(TrailingStopEngine.Decision.NONE); // days=2 < cooldown3
    }

    /** tracer bullet 9:卖出后 High 从剩余仓位重算,下一轮从新 High 起算。 */
    @Test
    void 卖出后High从剩余仓位重算_下一轮从新High起算() {
        TakeProfitParams params = shortCooldown();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        // 启动 High=160 → 连2日跌破卖出 20 份
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState();
        TrailingStopEngine.Step day1 = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.0"), params);
        TrailingStopEngine.Step day2 = TrailingStopEngine.evaluate(day1.newState(), pos(shares, invested), new BigDecimal("1.0"), params);
        assertThat(day2.decision()).isEqualTo(TrailingStopEngine.Decision.SELL);
        // 卖后剩 80 份 × nav 1.0 = 市值 80 → 重置 High=80(从剩余仓位起算,不是旧 160)
        TrailingStopEngine.State afterSell = day2.newState();
        assertThat(afterSell.roundHigh()).isEqualByComparingTo(new BigDecimal("80"));
        // 下一轮创新高:nav 1.0→1.05(市值84>80)→ High 上移到 84
        TrailingStopEngine.State nextRound = TrailingStopEngine.evaluate(afterSell,
                pos(new BigDecimal("80"), invested), new BigDecimal("1.05"), params).newState();
        assertThat(nextRound.roundHigh()).isEqualByComparingTo(new BigDecimal("84"));
    }

    private static TrailingStopEngine.Position pos(BigDecimal shares, BigDecimal invested) {
        return new TrailingStopEngine.Position(shares, shares, BigDecimal.ZERO, invested, BigDecimal.ZERO);
    }

    /**
     * tracer bullet 10:dca 基准每期金额 = 基金级每期定投金额(不再 plannedTotalAmount/月数);
     * passed 判定 Calmar 口径复用 BenchmarkCalculator.judgePassed。
     */
    @Test
    void dca基准每期金额等于定投金额_passed判定Calmar口径复用() {
        // 两月末扣款,每期 1000:day0 买 1000 份;day1 买 1000/1.2=833.33,合计 1833.33 份 × 1.2 = 2200;投入 2000,收益 0.1
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("1.2"));
        List<Instant> dates = List.of(
                Instant.parse("2025-01-31T00:00:00Z"),
                Instant.parse("2025-02-28T00:00:00Z"));

        BenchmarkMetrics dca = BenchmarkCalculator.dca(nav, dates, new BigDecimal("1000"));

        // 每期 1000(非总额/月数):收益 (2200-2000)/2000 = 0.1;单调上升回撤 0
        assertThat(dca.returnRate()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.01")));
        assertThat(dca.maxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);

        // passed 判定:策略 Calmar 须 ≥ dca Calmar(零回撤 dca 视作 +∞,策略有回撤则不通过)
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.20"), new BigDecimal("0.10"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                dca);
        assertThat(passed).isFalse(); // dca 零回撤 +∞,策略有回撤必输
    }

    /** 短冷却(2)便于连续触发,其余沿用宽基默认。 */
    private static TakeProfitParams shortCooldown() {
        return new TakeProfitParams(
                new BigDecimal("0.50"),
                TakeProfitParams.broadDefaults().pullbackTiers(),
                new BigDecimal("0.20"), new BigDecimal("0.40"), 2);
    }

    // ===== #58 行业差异化 + 定投暂停/恢复 =====

    /** 行业参数:启动40%、回撤40~60%→10%、卖25%、底仓25%、冷却2。 */
    private static TakeProfitParams sectorShortCooldown() {
        return new TakeProfitParams(
                new BigDecimal("0.40"),
                TakeProfitParams.sectorDefaults().pullbackTiers(),
                new BigDecimal("0.25"), new BigDecimal("0.25"), 2);
    }

    /**
     * #58 tracer bullet:行业止盈后暂停定投(下个月末 yield>10% 无 INVEST),收益跌回 10% 以下恢复(再下个月末有 INVEST)。
     * <p>每期 1000,行业参数(启动40%、回撤60~90%→12%、卖25%、冷却2)。M1末建仓 1000 份成本1.0=投入1000。
     * <ul>
     *   <li>M1末 nav1.0:INVEST 1000 份</li>
     *   <li>M2末 nav1.8:yield80%≥40%启动,High1800,档60~90%→12%,线1800×0.88=1584;1800>1584 未跌破</li>
     *   <li>M3日 nav1.55 连2日(市值2411<线2464跌破):卖25%;卖后剩1166.67份,yield 随 nav 变 → 暂停</li>
     *   <li>M3末 nav1.9:市值1166.67×1.9=2216,yield10.8%>10%→仍暂停,无 INVEST</li>
     *   <li>M4末 nav1.4:市值1166.67×1.4=1633,yield−18%≤10%→恢复,有 INVEST</li>
     * </ul>
     * 卖出放在 M3 非月末的相邻两日,M3末本身不再卖出,只体现暂停。
     */
    @Test
    void 行业止盈后暂停定投_收益跌回10百分比以下恢复() {
        List<BigDecimal> nav = List.of(
                new BigDecimal("1.0"),   // M1末 INVEST
                new BigDecimal("1.8"),   // M2末 INVEST + 启动(High2800,线2464)
                new BigDecimal("1.55"),  // M3日1 跌破1(mv2411<2464)
                new BigDecimal("1.55"),  // M3日2 跌破2 卖(非月末)
                new BigDecimal("1.9"),   // M3末 暂停(yield10.8%>10%,无 INVEST)
                new BigDecimal("1.4"));  // M4末 恢复(yield−18%≤10%,有 INVEST)
        List<Instant> dates = List.of(
                Instant.parse("2025-01-31T00:00:00Z"),
                Instant.parse("2025-02-28T00:00:00Z"),
                Instant.parse("2025-03-29T00:00:00Z"),
                Instant.parse("2025-03-30T00:00:00Z"),
                Instant.parse("2025-03-31T00:00:00Z"),
                Instant.parse("2025-04-30T00:00:00Z"));

        DcaTakeProfitResult result = DcaTakeProfitSimulator.simulate(
                nav, dates, new BigDecimal("1000"), sectorShortCooldown(), FundCategory.SECTOR);

        List<DcaTakeProfitResult.SimTrade> invests = result.trades().stream()
                .filter(t -> "INVEST".equals(t.source())).toList();
        // M1、M2、M4末 共 3 个 INVEST;M3末暂停无
        assertThat(invests).hasSize(3);
        assertThat(invests).noneSatisfy(t ->
                assertThat(t.date()).isEqualTo(Instant.parse("2025-03-31T00:00:00Z")));
        assertThat(invests).anySatisfy(t ->
                assertThat(t.date()).isEqualTo(Instant.parse("2025-04-30T00:00:00Z")));
        assertThat(result.trades().stream().anyMatch(t -> "SELL".equals(t.source()))).isTrue();
    }

    /** #58:宽基止盈后不暂停定投(一直定投不停)。 */
    @Test
    void 宽基止盈后继续定投不停() {
        // 宽基、短冷却;构造止盈后下个月末仍 INVEST
        List<BigDecimal> nav = List.of(
                new BigDecimal("1.0"),   // M1末 INVEST
                new BigDecimal("1.8"),   // M2末 启动(High1800,线1800×0.85=1530)
                new BigDecimal("1.5"),   // M3 跌破1(市值1500<1530)
                new BigDecimal("1.5"),   // M3末 跌破2 卖20%
                new BigDecimal("1.5"));  // M4末 应继续 INVEST(宽基不暂停)
        List<Instant> dates = List.of(
                Instant.parse("2025-01-31T00:00:00Z"),
                Instant.parse("2025-02-28T00:00:00Z"),
                Instant.parse("2025-03-30T00:00:00Z"),
                Instant.parse("2025-03-31T00:00:00Z"),
                Instant.parse("2025-04-30T00:00:00Z"));

        DcaTakeProfitResult result = DcaTakeProfitSimulator.simulate(
                nav, dates, new BigDecimal("1000"), shortCooldown(), FundCategory.BROAD_BASE);

        List<DcaTakeProfitResult.SimTrade> invests = result.trades().stream()
                .filter(t -> "INVEST".equals(t.source())).toList();
        // M1、M2、M4 都有 INVEST(M3末非月末买入点;M4末宽基不暂停)
        assertThat(invests).anySatisfy(t ->
                assertThat(t.date()).isEqualTo(Instant.parse("2025-04-30T00:00:00Z")));
    }

    // ===== #59 极端行情保护 =====

    /**
     * #59 tracer bullet 1:单日跌 ≥7% 且已盈利 → 卖 10~20%(用 15%),reason=EXTREME_MARKET_PROTECT,优先于移动止盈。
     * <p>100份成本1.0=投入100,已盈利(nav 1.6,yield60%)。单日跌7%(nav 1.6→1.488,dailyDrop=7.2%≥7%)→ 触发极端保护卖15份。
     */
    @Test
    void 单日跌7百分比以上且盈利_触发极端保护卖出15百分比() {
        TakeProfitParams params = shortCooldown();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        // 先推进到 nav1.6 启动并盈利(yield60%)
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState();
        assertThat(state.activated()).isTrue();

        // 单日跌7.2%(1.6→1.488),已盈利 → 极端保护卖 15 份
        ExtremeMarketInput extreme = new ExtremeMarketInput(new BigDecimal("0.072"), null);
        TrailingStopEngine.Step step = TrailingStopEngine.evaluate(
                state, pos(shares, invested), new BigDecimal("1.488"), params, extreme);
        assertThat(step.decision()).isEqualTo(TrailingStopEngine.Decision.SELL);
        assertThat(step.sellShares()).isEqualByComparingTo(new BigDecimal("15"));
    }

    /**
     * #59 tracer bullet 2:未盈利时单日跌 ≥7% 不触发极端保护。
     * <p>100份成本1.0=投入100,nav 0.9(亏损,yield−10%),单日跌8% → 不卖(未盈利)。
     */
    @Test
    void 未盈利单日跌7百分比以上_不触发极端保护() {
        TakeProfitParams params = shortCooldown();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        ExtremeMarketInput extreme = new ExtremeMarketInput(new BigDecimal("0.08"), null);
        TrailingStopEngine.Step step = TrailingStopEngine.evaluate(
                state, pos(shares, invested), new BigDecimal("0.9"), params, extreme);
        assertThat(step.decision()).isEqualTo(TrailingStopEngine.Decision.NONE);
    }

    /**
     * #59 tracer bullet 3:连续3日累计跌 ≥12% → 额外减仓(卖 15%),即使未触及移动止盈线。
     * <p>100份成本1.0,nav1.6启动盈利(yield60%)。3日累计跌12%(dailyDrop小但累计12%≥12%)→ 触发卖15份。
     */
    @Test
    void 连3日累计跌12百分比以上_触发额外减仓() {
        TakeProfitParams params = shortCooldown();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState();
        // 3日累计跌12%(单日跌不足7%,不触发单日规则;累计12%触发3日规则)
        ExtremeMarketInput extreme = new ExtremeMarketInput(new BigDecimal("0.04"), new BigDecimal("0.12"));
        TrailingStopEngine.Step step = TrailingStopEngine.evaluate(
                state, pos(shares, invested), new BigDecimal("1.5"), params, extreme);
        assertThat(step.decision()).isEqualTo(TrailingStopEngine.Decision.SELL);
    }

    /**
     * #59 tracer bullet 4:极端保护与移动止盈同日只出一类(极端保护优先)。
     * <p>构造同时满足极端保护(单日跌7%)与移动止盈(连2日跌破)的场景,应只出极端保护(优先级高)。
     */
    @Test
    void 极端保护与移动止盈同日_只出极端保护() {
        TakeProfitParams params = shortCooldown();
        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal shares = new BigDecimal("100");
        BigDecimal invested = new BigDecimal("100");

        // nav1.6 启动(High160,线136)
        state = TrailingStopEngine.evaluate(state, pos(shares, invested), new BigDecimal("1.6"), params).newState();
        // nav1.05:市值105>投入100(yield5%盈利),单日跌 (1.6−1.05)/1.6=34%≥7% → 极端保护触发
        // 同时 mv105<136 跌破止盈线,但只跌破1日(前日未跌破),移动止盈不触发;极端保护优先触发
        ExtremeMarketInput extreme = new ExtremeMarketInput(new BigDecimal("0.34"), null);
        TrailingStopEngine.Step step = TrailingStopEngine.evaluate(
                state, pos(shares, invested), new BigDecimal("1.05"), params, extreme);
        // 极端保护优先:卖15份(极端),不是移动止盈的20份
        assertThat(step.decision()).isEqualTo(TrailingStopEngine.Decision.SELL);
        assertThat(step.sellShares()).isEqualByComparingTo(new BigDecimal("15"));
    }
}
