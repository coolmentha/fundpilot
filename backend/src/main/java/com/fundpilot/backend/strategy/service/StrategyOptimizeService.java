package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.market.service.support.SecidFormat;
import com.fundpilot.backend.strategy.service.support.BacktestIndicatorCalculator;
import com.fundpilot.backend.strategy.service.support.BacktestParams;
import com.fundpilot.backend.strategy.service.support.BacktestResult;
import com.fundpilot.backend.strategy.service.support.BacktestSimulator;
import com.fundpilot.backend.strategy.service.support.BenchmarkCalculator;
import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;
import com.fundpilot.backend.strategy.service.support.FailureReason;
import com.fundpilot.backend.strategy.service.support.MarketIndicators;
import com.fundpilot.backend.strategy.service.support.MaxDrawdownCalculator;
import com.fundpilot.backend.strategy.service.support.OptimizeGridGenerator;
import com.fundpilot.backend.strategy.service.support.OptimizeParamRanker;
import com.fundpilot.backend.strategy.service.support.OptimizeParams;
import com.fundpilot.backend.strategy.service.support.OptimizeValidationResult;
import com.fundpilot.backend.strategy.service.support.RankedParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 策略参数寻优编排(issue #28):对一只基金从默认基准出发做网格搜索,
 * train 集 top-k 提名 → test 集 passed 过滤 + Calmar 择优,选出泛化最稳的参数,
 * 通过则生成策略草稿走标准 calibrate 落库。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>拉基金 + 1 年净值序列 + 跟踪指数 K 线(benchmarkKline),算行情指标(indicators)一次复用</li>
 *   <li>按时间 0.7:0.3 切 train/test 两段(净值/日期/指标各切一份)</li>
 *   <li>train 集用 {@link OptimizeParamRanker#rankTopK} 选 train Calmar 前 {@value #TOP_K} 名(携带 train 指标)</li>
 *   <li>每组候选跑 {@link #validate}(test simulate + MaxDrawdown + 三基准 + 归因 FailureReason);passed = reasons 为空</li>
 *   <li>passed 候选中按 test Calmar 择优(ADR-0011 top-k):null(零回撤 +∞)优先,否则数值大者</li>
 *   <li>有 passed 候选 → {@link StrategyConfigService#createDraft}(择优参数)+ {@link StrategyConfigService#calibrate},返回 strategyId</li>
 *   <li>无 passed 候选 → 抛 {@link ErrorCode#OPTIMIZATION_NO_VALID_PARAMS},打每组诊断日志</li>
 * </ol>
 *
 * <p>主方法不加 @Transactional(长事务,网格约 64 组回测);落库阶段复用 createDraft/calibrate(各自有事务)。
 *
 * <p><b>top-k 择优(ADR-0011)</b>:train 单点冠军可能是噪声产物,top-k 给 test 集多个候选,
 * 从"门槛"变"择优标尺"。代价:test 集参与选参数,独立性打折,但用 Calmar(稳健指标)择优风险可控。
 *
 * <p><b>状态分离(ADR-0007)</b>:寻优在 test 集(0.3 年)达标即成功,但落库走 calibrate(1 年全窗口)。
 * 两者窗口不同,可能出现"寻优成功但全窗口 CALIBRATION_FAILED"——职责分离的正常结果,非 bug。
 *
 * @see OptimizeGridGenerator
 * @see OptimizeParamRanker
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyOptimizeService {

    private static final double TRAIN_RATIO = 0.7;
    private static final MathContext MATH = MathContext.DECIMAL64;
    /** top-k 择优的 k:train 集风险调整收益前 k 名送 test 集择优落库(issue #28)。 */
    private static final int TOP_K = 5;

    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final MarketDataSource marketDataSource;
    private final Hs300BenchmarkProvider hs300BenchmarkProvider;
    private final StrategyConfigService strategyConfigService;

    /**
     * @param fundId 基金 id
     * @return 新建策略 id(test 集达标 → 落库草稿 + calibrate)
     * @throws BusinessException test 集未达标时抛 {@link ErrorCode#OPTIMIZATION_NO_VALID_PARAMS}
     */
    public Long optimize(Long fundId) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        BigDecimal plannedTotalAmount = Optional.ofNullable(fund.getPlannedTotalAmount()).orElse(BigDecimal.ZERO);
        if (plannedTotalAmount.signum() <= 0) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "基金 " + fundId + " 无计划仓位,无法寻优");
        }

        // 1 年净值窗口(循环外算一次复用)
        Instant end = Instant.now();
        Instant start = end.minus(BacktestWindow.BACKTEST_WINDOW_DAYS, ChronoUnit.DAYS);
        List<FundNavHistoryEntity> navHistory =
                fundNavHistoryRepository.findByFundEntity_IdAndNavDateBetweenOrderByNavDateAsc(fund.getId(), start, end);
        if (navHistory.size() < 2) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "基金 " + fundId + " 净值序列不足,无法寻优");
        }
        List<BigDecimal> navSequence = navHistory.stream().map(FundNavHistoryEntity::getAccumulatedNav).toList();
        List<Instant> navDates = navHistory.stream().map(FundNavHistoryEntity::getNavDate).toList();
        IndexKline benchmarkKline = fetchBenchmarkKline(fund);
        List<MarketIndicators> indicators = BacktestIndicatorCalculator.calculate(navSequence, navDates, benchmarkKline);

        // 0.7:0.3 切 train/test
        int split = Math.max(1, (int) Math.round(navSequence.size() * TRAIN_RATIO));
        if (split >= navSequence.size()) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "基金 " + fundId + " 净值序列切分后 test 集为空,无法寻优");
        }
        List<BigDecimal> trainNav = navSequence.subList(0, split);
        List<Instant> trainDates = navDates.subList(0, split);
        List<MarketIndicators> trainIndicators = indicators.subList(0, split);
        List<BigDecimal> testNav = navSequence.subList(split, navSequence.size());
        List<Instant> testDates = navDates.subList(split, navSequence.size());
        List<MarketIndicators> testIndicators = indicators.subList(split, indicators.size());

        // train 集选 top-k 候选(issue #28 top-k 择优):train 单点冠军可能是噪声产物,
        // top-k 给 test 集多个候选,passed 过滤 + test Calmar 择优落库
        List<OptimizeParams> grid = OptimizeGridGenerator.generate(fund.getFundCategory());
        List<RankedParam> topCandidates = OptimizeParamRanker.rankTopK(
                trainNav, trainDates, trainIndicators, plannedTotalAmount, grid, TOP_K);
        if (topCandidates.isEmpty()) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "基金 " + fundId + " 寻优未找到有回撤的候选参数(train 集全部回撤为 0)");
        }

        // 每组候选跑 test 集验证,passed 过滤后在 passed 候选中按 test Calmar 择优
        List<OptimizeValidationResult> validations = new ArrayList<>();
        for (RankedParam ranked : topCandidates) {
            validations.add(validate(ranked, testNav, testDates, testIndicators, plannedTotalAmount));
        }
        Optional<OptimizeValidationResult> bestOpt = validations.stream()
                .filter(OptimizeValidationResult::passed)
                .max(Comparator.comparing(OptimizeValidationResult::testStrategyCalmar, calmarComparator()));
        if (bestOpt.isEmpty()) {
            // 全部候选 test 集未达标:打每组诊断日志后报错
            logAllCandidatesFailed(fundId, topCandidates, validations);
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    buildFailureMessage(fundId, validations, topCandidates));
        }
        OptimizeValidationResult validation = bestOpt.get();
        OptimizeParams best = validation.bestParams();

        log.info("寻优 train 集选 top-{} 共 {} 组通过 test,择优落库 fund={} best=[tier1={},tier4={},stopLoss={}] "
                        + "train[return={},dd={},calmar={}] test[strategyReturn={},strategyDd={},calmar={}]",
                TOP_K, validations.stream().filter(OptimizeValidationResult::passed).count(),
                fundId, best.tier1Drawdown(), best.tier4Drawdown(), best.stopLossPullbackPercent(),
                validation.trainReturn(), validation.trainMaxDrawdown(), validation.trainCalmar(),
                validation.testStrategyReturn(), validation.testStrategyMaxDrawdown(),
                validation.testStrategyCalmar());

        // test 达标 → 落库:createDraft(最优参数)+ calibrate(1 年全窗口留痕)
        Long strategyId = strategyConfigService.createDraft(fundId, toConfigRequest(best));
        strategyConfigService.calibrate(strategyId);
        return strategyId;
    }

    /**
     * test 集样本外验证 + 诊断(issue #28 诊断增强 + top-k 择优)。
     *
     * <p>train 指标从 {@link RankedParam} 传入(rankTopK 已算,不重跑),在 test 集算策略指标 +
     * 三基准,按维度独立归因收集 {@link FailureReason}。passed = reasons 为空,语义复刻
     * {@link BenchmarkCalculator#judgePassed}(收益严格 > hs300/dca,策略 Calmar >= dca Calmar)——
     * reasons 是唯一判定源,避免判定与诊断两个源漂移。allIn 已退出判定,仍计算落库供展示基金自然回撤。
     *
     * <p>test 净值不足 2 条时基准计算返零会掩盖真因,
     * 单独标 {@link FailureReason#TEST_SPLIT_TOO_SHORT} 并跳过策略/基准计算。
     */
    private OptimizeValidationResult validate(RankedParam ranked,
                                              List<BigDecimal> testNav, List<Instant> testDates,
                                              List<MarketIndicators> testIndicators,
                                              BigDecimal plannedTotalAmount) {
        OptimizeParams best = ranked.params();
        BigDecimal trainReturn = ranked.trainReturn();
        BigDecimal trainMaxDrawdown = ranked.trainMaxDrawdown();
        BigDecimal trainCalmar = ranked.trainCalmar();
        BacktestParams params = toBacktestParams(best, plannedTotalAmount);

        // test 边界:净值不足 2 条无法有效验证,基准返零会掩盖真因
        if (testNav.size() < 2) {
            return new OptimizeValidationResult(false, best,
                    trainReturn, trainMaxDrawdown, trainCalmar,
                    null, null, null, null, null, null, null,
                    List.of(FailureReason.TEST_SPLIT_TOO_SHORT));
        }

        // test 策略指标
        BacktestResult testResult = BacktestSimulator.simulate(testNav, testDates, testIndicators, params);
        BigDecimal testStrategyReturn = testResult.strategyReturn();
        BigDecimal testStrategyMaxDrawdown = MaxDrawdownCalculator.calculate(testResult.dailyValues());

        // 三基准:allIn/dca 用 test 净值重算,hs300 用 test 窗口。allIn 已退出判定,仍算供展示
        BenchmarkMetrics allIn = BenchmarkCalculator.allIn(testNav);
        BenchmarkMetrics dca = BenchmarkCalculator.dca(testNav, testDates, plannedTotalAmount);
        Instant testStart = testDates.get(0);
        Instant testEnd = testDates.get(testDates.size() - 1);
        BenchmarkMetrics hs300 = hs300BenchmarkProvider.fetch(testStart, testEnd);

        // 归因:按维度独立收集命中项(语义复刻 judgePassed 取反:收益须严格 > hs300/dca,Calmar 须 >= dca)
        // Calmar 三态:null=+∞(零回撤)。dca +∞ 时策略须也 +∞ 才不劣于;策略 +∞ 而 dca 有限值时策略赢
        BigDecimal strategyCalmar = BenchmarkCalculator.calmarRatio(testStrategyReturn, testStrategyMaxDrawdown);
        BigDecimal dcaCalmar = BenchmarkCalculator.calmarRatio(dca.returnRate(), dca.maxDrawdown());
        List<FailureReason> reasons = new ArrayList<>();
        if (testStrategyReturn.compareTo(hs300.returnRate()) <= 0) {
            reasons.add(FailureReason.TEST_RETURN_BELOW_HS300);
        }
        if (testStrategyReturn.compareTo(dca.returnRate()) <= 0) {
            reasons.add(FailureReason.TEST_RETURN_BELOW_DCA);
        }
        boolean calmarOk = dcaCalmar == null ? strategyCalmar == null
                : (strategyCalmar == null || strategyCalmar.compareTo(dcaCalmar) >= 0);
        if (!calmarOk) {
            reasons.add(FailureReason.TEST_CALMAR_BELOW_DCA);
        }
        return new OptimizeValidationResult(reasons.isEmpty(), best,
                trainReturn, trainMaxDrawdown, trainCalmar,
                testStrategyReturn, testStrategyMaxDrawdown, strategyCalmar, dcaCalmar,
                hs300, allIn, dca,
                List.copyOf(reasons));
    }

    /** OptimizeParams → BacktestParams(与 OptimizeParamRanker.toBacktestParams 同源)。 */
    private static BacktestParams toBacktestParams(OptimizeParams p, BigDecimal plannedTotalAmount) {
        return new BacktestParams(
                p.tier1Drawdown(), p.tier2Drawdown(), p.tier3Drawdown(), p.tier4Drawdown(),
                p.tier1Ratio(), p.tier2Ratio(), p.tier3Ratio(), p.tier4Ratio(),
                p.weeklyCoolDownThreshold(), p.stopLossPullbackPercent(),
                plannedTotalAmount, p.fundCategory(), p.fundSubType());
    }

    /** BenchmarkMetrics 收益(null 安全,日志拼装用)。 */
    private static BigDecimal benchmarkReturn(BenchmarkMetrics m) {
        return m == null ? null : m.returnRate();
    }

    /** BenchmarkMetrics 回撤(null 安全,日志拼装用)。 */
    private static BigDecimal benchmarkDrawdown(BenchmarkMetrics m) {
        return m == null ? null : m.maxDrawdown();
    }

    /**
     * 打 top-k 全部候选的诊断日志(全部未达标时)。每组一行,含 train/test 指标与未达标原因,
     * 供区分"train 冠军 test 翻车(过拟合)"vs"全组普遍平庸(regime)"。
     */
    private void logAllCandidatesFailed(Long fundId, List<RankedParam> candidates,
                                        List<OptimizeValidationResult> validations) {
        for (int i = 0; i < candidates.size(); i++) {
            RankedParam c = candidates.get(i);
            OptimizeValidationResult v = validations.get(i);
            log.warn("寻优 top-{} 候选未达标 fund={} rank={} params=[tier1={},tier4={},stopLoss={}] "
                            + "train[return={},dd={},calmar={}] test[strategyReturn={},strategyDd={},calmar={}] "
                            + "reasons={}",
                    i + 1, fundId, i + 1, c.params().tier1Drawdown(), c.params().tier4Drawdown(),
                    c.params().stopLossPullbackPercent(),
                    c.trainReturn(), c.trainMaxDrawdown(), c.trainCalmar(),
                    v.testStrategyReturn(), v.testStrategyMaxDrawdown(), v.testStrategyCalmar(),
                    v.reasons());
        }
    }

    /**
     * 拼失败 message(前端可见,精简版):top-k 全部未达标时,展示候选数 + 最优组(train Calmar 最高)的
     * train/test 摘要。完整每组诊断进日志(logAllCandidatesFailed),message 只放最有信息量的一行。
     */
    private static String buildFailureMessage(Long fundId, List<OptimizeValidationResult> validations,
                                              List<RankedParam> candidates) {
        // 最优组 = train Calmar 最高(候选已按 train Calmar 降序,取第一个)
        RankedParam top = candidates.get(0);
        OptimizeValidationResult topValidation = validations.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("基金 ").append(fundId).append(" 寻优 ").append(candidates.size())
                .append(" 组候选 test 集全部未达标,最优组(train Calmar=")
                .append(calmarStr(top.trainCalmar())).append(")");
        if (topValidation.testStrategyReturn() != null) {
            sb.append(": test 策略收益 ").append(pct(topValidation.testStrategyReturn()))
                    .append("/回撤 ").append(pct(topValidation.testStrategyMaxDrawdown()))
                    .append("/Calmar ").append(calmarStr(topValidation.testStrategyCalmar()))
                    .append(" vs dca/Calmar ").append(calmarStr(topValidation.dcaCalmar()))
                    .append(" 原因 ").append(topValidation.reasons());
        } else {
            sb.append(": ").append(topValidation.reasons());
        }
        return sb.toString();
    }

    /** Calmar 比较器:test Calmar 择优时 null(零回撤 +∞)视为最大,优先选;否则按数值降序选最大。 */
    /** test Calmar 择优比较器:null(零回撤 +∞)视为最大优先选;非 null 按数值大者优先。 */
    private static Comparator<BigDecimal> calmarComparator() {
        // nullsLast(naturalOrder):null 排末尾即自然序最大位置;max(comparator) 据此选 null 优先
        return Comparator.nullsLast(Comparator.naturalOrder());
    }

    /** BigDecimal → 百分比字符串(0.035 → "3.50%"),null → "N/A"。 */
    private static String pct(BigDecimal v) {
        if (v == null) {
            return "N/A";
        }
        return v.multiply(BigDecimal.valueOf(100)).setScale(2, MATH.getRoundingMode()) + "%";
    }

    /** Calmar 比率字符串(保留 2 位小数),null → "∞"(零回撤,+∞)。 */
    private static String calmarStr(BigDecimal v) {
        if (v == null) {
            return "∞";
        }
        return v.setScale(2, MATH.getRoundingMode()).toPlainString();
    }

    private static StrategyConfigRequest toConfigRequest(OptimizeParams p) {
        return new StrategyConfigRequest(
                p.tier1Drawdown(), p.tier2Drawdown(), p.tier3Drawdown(), p.tier4Drawdown(),
                p.tier1Ratio(), p.tier2Ratio(), p.tier3Ratio(), p.tier4Ratio(),
                p.weeklyCoolDownThreshold(), p.stopLossPullbackPercent());
    }

    /** 拉跟踪指数 K 线供指标计算;无 benchmarkIndexCode 或拉取失败时返 null(量能类指标降级)。 */
    private IndexKline fetchBenchmarkKline(FundEntity fund) {
        String code = fund.getBenchmarkIndexCode();
        if (code == null || code.isBlank()) {
            return null;
        }
        String secid = SecidFormat.fromIndexCode(code).orElse(code);
        try {
            return marketDataSource.fetchIndexKline(secid, "6");
        } catch (RuntimeException ex) {
            log.warn("寻优拉取跟踪指数 K 线失败 code={} 量能类指标降级: {}", code, ex.getMessage());
            return null;
        }
    }
}
