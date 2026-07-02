package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.strategy.service.support.BenchmarkCalculator;
import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;
import com.fundpilot.backend.strategy.service.support.DcaTakeProfitResult;
import com.fundpilot.backend.strategy.service.support.DcaTakeProfitSimulator;
import com.fundpilot.backend.strategy.service.support.MaxDrawdownCalculator;
import com.fundpilot.backend.strategy.service.support.OptimizeGridGenerator;
import com.fundpilot.backend.strategy.service.support.OptimizeParamRanker;
import com.fundpilot.backend.strategy.service.support.OptimizeParams;
import com.fundpilot.backend.strategy.service.support.RankedParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 策略参数寻优编排(ADR-0015 重写,issue #63):搜索维度改为移动止盈参数
 * (启动门槛/卖出比例/底仓比例/冷却期),样外验证 0.7:0.3 切分。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>拉基金 + 1 年净值序列</li>
 *   <li>按时间 0.7:0.3 切 train/test 两段</li>
 *   <li>train 集用 {@link OptimizeParamRanker#rankTopK} 选 Calmar 前 k 名</li>
 *   <li>每组候选在 test 集回测 + {@link BenchmarkCalculator#judgePassed} 判 passed
 *       (策略 Calmar 须 ≥ dca Calmar,收益须 > hs300/dca)</li>
 *   <li>passed 候选中按 test Calmar 择优 → {@link StrategyConfigService#createDraft} + {@link StrategyConfigService#calibrate}</li>
 *   <li>无 passed 候选 → 抛 {@link ErrorCode#OPTIMIZATION_NO_VALID_PARAMS}</li>
 * </ol>
 *
 * <p>状态分离(ADR-0007):寻优 test 集达标即成功,落库走 calibrate(1 年全窗口),窗口不同可能"寻优成功但全窗口 CALIBRATION_FAILED"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyOptimizeService {

    private static final double TRAIN_RATIO = 0.7;
    private static final int TOP_K = 5;

    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final Hs300BenchmarkProvider hs300BenchmarkProvider;
    private final StrategyConfigService strategyConfigService;

    /**
     * 寻优入口。
     *
     * @param fundId 基金 id
     * @return 新建策略 id(test 集达标 → 落库草稿 + calibrate)
     * @throws BusinessException test 集未达标抛 {@link ErrorCode#OPTIMIZATION_NO_VALID_PARAMS}
     */
    public Long optimize(Long fundId) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        BigDecimal dcaAmount = fund.getDcaAmount() != null ? fund.getDcaAmount() : BigDecimal.ZERO;
        if (dcaAmount.signum() <= 0) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "基金无每期定投金额,无法寻优 fund=" + fundId);
        }

        Instant end = Instant.now();
        Instant start = end.minus(365, ChronoUnit.DAYS);
        List<FundNavHistoryEntity> navHistory =
                fundNavHistoryRepository.findByFundEntity_IdAndNavDateBetweenOrderByNavDateAsc(fund.getId(), start, end);
        if (navHistory.size() < 2) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "净值序列不足,无法寻优 fund=" + fundId);
        }
        List<BigDecimal> nav = navHistory.stream().map(FundNavHistoryEntity::getAccumulatedNav).toList();
        List<Instant> dates = navHistory.stream().map(FundNavHistoryEntity::getNavDate).toList();

        int split = (int) Math.round(nav.size() * TRAIN_RATIO);
        split = Math.max(1, Math.min(split, nav.size() - 1));
        List<BigDecimal> trainNav = nav.subList(0, split);
        List<Instant> trainDates = dates.subList(0, split);
        List<BigDecimal> testNav = nav.subList(split, nav.size());
        List<Instant> testDates = dates.subList(split, dates.size());

        List<OptimizeParams> grid = OptimizeGridGenerator.generate(fund.getFundCategory());
        List<RankedParam> ranked = OptimizeParamRanker.rankTopK(
                trainNav, trainDates, dcaAmount, fund.getFundCategory(), grid, TOP_K);
        log.info("寻优 train 集选 top-{} fund={} candidates={}", TOP_K, fundId, ranked.size());

        // test 集 passed 过滤 + Calmar 择优
        // 三基准统一用 test 段窗口(spec:样外验证在 test 集),避免 hs300 用全窗口污染 passed 判定
        Instant testStart = testDates.isEmpty() ? start : testDates.get(0);
        Instant testEnd = testDates.isEmpty() ? end : testDates.get(testDates.size() - 1);
        BenchmarkMetrics hs300 = hs300BenchmarkProvider.fetch(testStart, testEnd);
        BenchmarkMetrics dca = BenchmarkCalculator.dca(testNav, testDates, dcaAmount);
        BenchmarkMetrics allIn = BenchmarkCalculator.allIn(testNav);
        OptimizeParams best = null;
        BigDecimal bestCalmar = null;
        for (RankedParam candidate : ranked) {
            DcaTakeProfitResult result = DcaTakeProfitSimulator.simulate(
                    testNav, testDates, dcaAmount, candidate.params().toTakeProfitParams(), fund.getFundCategory());
            BigDecimal ret = result.strategyReturn();
            BigDecimal mdd = MaxDrawdownCalculator.calculate(result.dailyValues());
            boolean passed = BenchmarkCalculator.judgePassed(ret, mdd, hs300, allIn, dca);
            BigDecimal calmar = BenchmarkCalculator.calmarRatio(ret, mdd);
            log.info("寻优 test 集 fund={} params=[threshold={},sellRatio={},cooldown={}] return={} mdd={} passed={}",
                    fundId, candidate.params().activationThreshold(), candidate.params().sellRatio(),
                    candidate.params().cooldownDays(), ret, mdd, passed);
            if (passed && (best == null || compareCalmar(calmar, bestCalmar) > 0)) {
                best = candidate.params();
                bestCalmar = calmar;
            }
        }
        if (best == null) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "寻优无 passed 候选 fund=" + fundId);
        }
        Long strategyId = strategyConfigService.createDraft(fundId, toConfigRequest(best));
        strategyConfigService.calibrate(strategyId);
        log.info("寻优落库 fund={} strategyId={} best=[threshold={},sellRatio={},cooldown={}]",
                fundId, strategyId, best.activationThreshold(), best.sellRatio(), best.cooldownDays());
        return strategyId;
    }

    /** Calmar 比较:null(+∞)最大;否则数值大者优。 */
    private static int compareCalmar(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    private static StrategyConfigRequest toConfigRequest(OptimizeParams p) {
        BigDecimal tier4Yield = p.pullbackTiers().size() >= 4 ? p.pullbackTiers().get(3).minYield() : null;
        BigDecimal tier4Ratio = p.pullbackTiers().size() >= 4 ? p.pullbackTiers().get(3).ratio() : null;
        return new StrategyConfigRequest(
                p.activationThreshold(), p.pullbackTiers().size(),
                p.pullbackTiers().get(0).minYield(), p.pullbackTiers().get(0).ratio(),
                p.pullbackTiers().size() >= 2 ? p.pullbackTiers().get(1).minYield() : null,
                p.pullbackTiers().size() >= 2 ? p.pullbackTiers().get(1).ratio() : null,
                p.pullbackTiers().size() >= 3 ? p.pullbackTiers().get(2).minYield() : null,
                p.pullbackTiers().size() >= 3 ? p.pullbackTiers().get(2).ratio() : null,
                tier4Yield, tier4Ratio,
                p.sellRatio(), p.floorRatio(), p.cooldownDays());
    }
}