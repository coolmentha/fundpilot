package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.repository.StrategyBacktestRepository;
import com.fundpilot.backend.strategy.service.support.BacktestParams;
import com.fundpilot.backend.strategy.service.support.BacktestResult;
import com.fundpilot.backend.strategy.service.support.BacktestSimulator;
import com.fundpilot.backend.strategy.service.support.BenchmarkCalculator;
import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;
import com.fundpilot.backend.strategy.service.support.MaxDrawdownCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link StrategyBacktestService} 默认实现(issue #11):回测引擎编排。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>取策略版本(含四档参数)+ 基金 {@code plannedTotalAmount}</li>
 *   <li>窗口降级:基金最早净值日期晚于 window.start 时,start 降级为最早日期(issue #11 不报错)</li>
 *   <li>拉净值序列,不足两点返回零指标 + passed=false(无法回测,但仍落库留痕)</li>
 *   <li>{@link BacktestSimulator} 模拟策略执行 → 策略收益 + 逐日市值</li>
 *   <li>{@link MaxDrawdownCalculator} 算策略最大回撤</li>
 *   <li>三条基准:all-in / DCA(纯函数)+ 沪深300({@link Hs300BenchmarkProvider})</li>
 *   <li>{@link BenchmarkCalculator#judgePassed} 判定 passed(收益严格大于三条 且 回撤 ≤ all-in)</li>
 *   <li>落 {@link StrategyBacktestEntity} 并返回</li>
 * </ol>
 *
 * <p>#12 完成后,步骤 4 的 {@link BacktestSimulator} 应重构为调用 {@code DisciplineStrategyService.evaluateSignal}。
 */
@Service
public class DefaultStrategyBacktestService implements StrategyBacktestService {

    private final FundStrategyRepository fundStrategyRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final StrategyBacktestRepository strategyBacktestRepository;
    private final Hs300BenchmarkProvider hs300BenchmarkProvider;

    public DefaultStrategyBacktestService(FundStrategyRepository fundStrategyRepository,
                                          FundNavHistoryRepository fundNavHistoryRepository,
                                          StrategyBacktestRepository strategyBacktestRepository,
                                          Hs300BenchmarkProvider hs300BenchmarkProvider) {
        this.fundStrategyRepository = fundStrategyRepository;
        this.fundNavHistoryRepository = fundNavHistoryRepository;
        this.strategyBacktestRepository = strategyBacktestRepository;
        this.hs300BenchmarkProvider = hs300BenchmarkProvider;
    }

    @Override
    @Transactional
    public StrategyBacktestEntity run(Long strategyId, BacktestWindow window) {
        FundStrategyEntity strategy = fundStrategyRepository.findById(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("strategy_id=" + strategyId + " 不存在"));
        FundEntity fund = strategy.getFundEntity();
        BigDecimal plannedTotalAmount = Optional.ofNullable(fund.getPlannedTotalAmount())
                .orElse(BigDecimal.ZERO);

        // 窗口降级:基金成立不满 window.start 时,起始日降级为最早可用净值日期
        Instant start = window.startDate();
        Instant end = window.endDate();
        Instant earliest = fundNavHistoryRepository.findEarliestNavDate(fund.getId()).orElse(null);
        if (earliest != null && earliest.isAfter(start)) {
            start = earliest;
        }

        List<FundNavHistoryEntity> navHistory =
                fundNavHistoryRepository.findByFundEntity_IdAndNavDateBetweenOrderByNavDateAsc(fund.getId(), start, end);

        StrategyBacktestEntity entity = new StrategyBacktestEntity();
        entity.setFundStrategyEntity(strategy);
        entity.setBacktestStartDate(start);
        entity.setBacktestEndDate(end);

        if (navHistory.size() < 2 || plannedTotalAmount.signum() <= 0) {
            // 净值序列不足或无计划仓位:无法回测,落零指标 + passed=false 留痕
            return saveZero(entity);
        }

        List<BigDecimal> navSequence = navHistory.stream().map(FundNavHistoryEntity::getAccumulatedNav).toList();
        List<Instant> navDates = navHistory.stream().map(FundNavHistoryEntity::getNavDate).toList();

        BacktestParams params = toParams(strategy, plannedTotalAmount);
        BacktestResult result = BacktestSimulator.simulate(navSequence, params);
        BigDecimal strategyReturn = result.strategyReturn();
        BigDecimal strategyMaxDrawdown = MaxDrawdownCalculator.calculate(result.dailyValues());

        BenchmarkMetrics allIn = BenchmarkCalculator.allIn(navSequence);
        BenchmarkMetrics dca = BenchmarkCalculator.dca(navSequence, navDates, plannedTotalAmount);
        BenchmarkMetrics hs300 = hs300BenchmarkProvider.fetch(start, end);
        boolean passed = BenchmarkCalculator.judgePassed(strategyReturn, strategyMaxDrawdown, hs300, allIn, dca);

        entity.setStrategyReturn(strategyReturn);
        entity.setStrategyMaxDrawdown(strategyMaxDrawdown);
        entity.setBenchmarkAllInReturn(allIn.returnRate());
        entity.setBenchmarkAllInMaxDrawdown(allIn.maxDrawdown());
        entity.setBenchmarkDcaReturn(dca.returnRate());
        entity.setBenchmarkDcaMaxDrawdown(dca.maxDrawdown());
        entity.setBenchmarkHs300Return(hs300.returnRate());
        entity.setBenchmarkHs300MaxDrawdown(hs300.maxDrawdown());
        entity.setPassed(passed);
        return strategyBacktestRepository.save(entity);
    }

    private StrategyBacktestEntity saveZero(StrategyBacktestEntity entity) {
        entity.setStrategyReturn(BigDecimal.ZERO);
        entity.setStrategyMaxDrawdown(BigDecimal.ZERO);
        entity.setBenchmarkAllInReturn(BigDecimal.ZERO);
        entity.setBenchmarkAllInMaxDrawdown(BigDecimal.ZERO);
        entity.setBenchmarkDcaReturn(BigDecimal.ZERO);
        entity.setBenchmarkDcaMaxDrawdown(BigDecimal.ZERO);
        entity.setBenchmarkHs300Return(BigDecimal.ZERO);
        entity.setBenchmarkHs300MaxDrawdown(BigDecimal.ZERO);
        entity.setPassed(false);
        return strategyBacktestRepository.save(entity);
    }

    private static BacktestParams toParams(FundStrategyEntity strategy, BigDecimal plannedTotalAmount) {
        return new BacktestParams(
                strategy.getTier1Drawdown(), strategy.getTier2Drawdown(),
                strategy.getTier3Drawdown(), strategy.getTier4Drawdown(),
                strategy.getTier1Ratio(), strategy.getTier2Ratio(),
                strategy.getTier3Ratio(), strategy.getTier4Ratio(),
                strategy.getWeeklyCoolDownThreshold(),
                strategy.getStopLossPullbackPercent(),
                plannedTotalAmount);
    }
}
