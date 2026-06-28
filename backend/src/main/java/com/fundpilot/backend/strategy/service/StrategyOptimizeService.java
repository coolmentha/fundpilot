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
import com.fundpilot.backend.strategy.service.support.MarketIndicators;
import com.fundpilot.backend.strategy.service.support.MaxDrawdownCalculator;
import com.fundpilot.backend.strategy.service.support.OptimizeGridGenerator;
import com.fundpilot.backend.strategy.service.support.OptimizeParamRanker;
import com.fundpilot.backend.strategy.service.support.OptimizeParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 策略参数寻优编排(issue #28):对一只基金从默认基准出发做网格搜索,
 * 找出风险调整收益最高的参数,并用样本外验证(test 集 passed)确认泛化能力,
 * 通过则生成策略草稿走标准 calibrate 落库。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>拉基金 + 1 年净值序列 + 跟踪指数 K 线(benchmarkKline),算行情指标(indicators)一次复用</li>
 *   <li>按时间 0.7:0.3 切 train/test 两段(净值/日期/指标各切一份)</li>
 *   <li>train 集复用 {@link OptimizeParamRanker#rankBest} 选最优参数(风险调整收益最高)</li>
 *   <li>test 集跑 simulate + MaxDrawdown + judgePassed(test 的 allIn/dca/hs300 用 test 净值/窗口重算)</li>
 *   <li>test passed=true → {@link StrategyConfigService#createDraft}(最优参数)+ {@link StrategyConfigService#calibrate},返回 strategyId</li>
 *   <li>test passed=false → 抛 {@link ErrorCode#OPTIMIZATION_NO_VALID_PARAMS},不创建任何草稿</li>
 * </ol>
 *
 * <p>主方法不加 @Transactional(长事务,网格约 64 组回测);落库阶段复用 createDraft/calibrate(各自有事务)。
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

        // train 集选最优参数
        List<OptimizeParams> grid = OptimizeGridGenerator.generate(fund.getFundCategory());
        Optional<OptimizeParams> bestOpt = OptimizeParamRanker.rankBest(
                trainNav, trainDates, trainIndicators, plannedTotalAmount, grid);
        if (bestOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "基金 " + fundId + " 寻优未找到有回撤的候选参数(train 集全部回撤为 0)");
        }
        OptimizeParams best = bestOpt.get();

        // test 集验证泛化:test 的 allIn/dca 用 test 净值重算,hs300 用 test 窗口
        if (!testPassed(best, testNav, testDates, testIndicators, plannedTotalAmount)) {
            throw new BusinessException(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS,
                    "基金 " + fundId + " 寻优 test 集未达标,无泛化能力");
        }

        // test 达标 → 落库:createDraft(最优参数)+ calibrate(1 年全窗口留痕)
        Long strategyId = strategyConfigService.createDraft(fundId, toConfigRequest(best));
        strategyConfigService.calibrate(strategyId);
        log.info("寻优成功 fund={} strategyId={} 最优参数 tier1={}/tier4={}/stopLoss={}",
                fundId, strategyId, best.tier1Drawdown(), best.tier4Drawdown(), best.stopLossPullbackPercent());
        return strategyId;
    }

    /** test 集 passed 判定:策略收益跑赢三条基准(allIn/dca 用 test 净值,hs300 用 test 窗口)且回撤 ≤ allIn。 */
    private boolean testPassed(OptimizeParams best, List<BigDecimal> testNav, List<Instant> testDates,
                               List<MarketIndicators> testIndicators, BigDecimal plannedTotalAmount) {
        BacktestParams params = new BacktestParams(
                best.tier1Drawdown(), best.tier2Drawdown(), best.tier3Drawdown(), best.tier4Drawdown(),
                best.tier1Ratio(), best.tier2Ratio(), best.tier3Ratio(), best.tier4Ratio(),
                best.weeklyCoolDownThreshold(), best.stopLossPullbackPercent(),
                plannedTotalAmount, best.fundCategory(), best.fundSubType());
        BacktestResult result = BacktestSimulator.simulate(testNav, testDates, testIndicators, params);
        BigDecimal strategyReturn = result.strategyReturn();
        BigDecimal strategyMaxDrawdown = MaxDrawdownCalculator.calculate(result.dailyValues());

        BenchmarkMetrics allIn = BenchmarkCalculator.allIn(testNav);
        BenchmarkMetrics dca = BenchmarkCalculator.dca(testNav, testDates, plannedTotalAmount);
        Instant testStart = testDates.get(0);
        Instant testEnd = testDates.get(testDates.size() - 1);
        BenchmarkMetrics hs300 = hs300BenchmarkProvider.fetch(testStart, testEnd);
        return BenchmarkCalculator.judgePassed(strategyReturn, strategyMaxDrawdown, hs300, allIn, dca);
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
