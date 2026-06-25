package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.IllegalStateTransitionException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundStrategyActivationEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundStrategyActivationRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.repository.StrategyBacktestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 策略参数配置服务(issue #10):CRUD + 状态机管理。
 *
 * <h3>状态机</h3>
 * <pre>
 * PENDING_CALIBRATION --calibrate--> CALIBRATED --activate--> EFFECTIVE
 *        ^                                  ^                   |
 *        |                                  +---回退(CLEARED)---+
 *        +-------------------retire/回退--------------------+
 * </pre>
 * <p>同基金同时最多一份 EFFECTIVE(数据库 {@code uq_fund_strategy_effective} 兜底)。
 * activate 新版本时旧 EFFECTIVE 自动回退 CALIBRATED;CLEARED→PENDING_HOLDING 时全员回退 PENDING_CALIBRATION。
 */
@Service
public class StrategyConfigService {

    private final FundStrategyRepository fundStrategyRepository;
    private final FundRepository fundRepository;
    private final StrategyBacktestService strategyBacktestService;
    private final StrategyBacktestRepository strategyBacktestRepository;
    private final FundStrategyActivationRepository fundStrategyActivationRepository;

    public StrategyConfigService(FundStrategyRepository fundStrategyRepository,
                                 FundRepository fundRepository,
                                 StrategyBacktestService strategyBacktestService,
                                 StrategyBacktestRepository strategyBacktestRepository,
                                 FundStrategyActivationRepository fundStrategyActivationRepository) {
        this.fundStrategyRepository = fundStrategyRepository;
        this.fundRepository = fundRepository;
        this.strategyBacktestService = strategyBacktestService;
        this.strategyBacktestRepository = strategyBacktestRepository;
        this.fundStrategyActivationRepository = fundStrategyActivationRepository;
    }

    /**
     * 新建策略草稿,状态 PENDING_CALIBRATION。
     */
    @Transactional
    public Long createDraft(Long fundId, StrategyConfigRequest request) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new IllegalArgumentException("fund_id=" + fundId + " 不存在"));
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setFundEntity(fund);
        strategy.setStatus(StrategyParamStatus.PENDING_CALIBRATION);
        applyRequest(strategy, request);
        return fundStrategyRepository.save(strategy).getId();
    }

    /**
     * 更新草稿参数——仅 PENDING_CALIBRATION 状态可改,否则抛 {@link IllegalStateTransitionException}。
     */
    @Transactional
    public void updateDraft(Long strategyId, StrategyConfigRequest request) {
        FundStrategyEntity strategy = requireStrategy(strategyId);
        if (strategy.getStatus() != StrategyParamStatus.PENDING_CALIBRATION) {
            throw new IllegalStateTransitionException(
                    strategy.getStatus().name(), "PENDING_CALIBRATION(可改参数)");
        }
        applyRequest(strategy, request);
        fundStrategyRepository.save(strategy);
    }

    /**
     * 列出某基金所有策略版本。
     */
    @Transactional(readOnly = true)
    public List<FundStrategyEntity> listByFund(Long fundId) {
        return fundStrategyRepository.findByFundEntity_Id(fundId);
    }

    /**
     * 查某基金当前 EFFECTIVE 策略(最多一份)。
     */
    @Transactional(readOnly = true)
    public Optional<FundStrategyEntity> findActive(Long fundId) {
        return fundStrategyRepository.findByFundEntity_IdAndStatus(fundId, StrategyParamStatus.EFFECTIVE);
    }

    /**
     * 校准:PENDING_CALIBRATION → CALIBRATED,同步触发自动回测(过去一年,不足降级),
     * 落 {@link StrategyBacktestEntity}。非 PENDING_CALIBRATION 状态抛 {@link IllegalStateTransitionException}。
     */
    @Transactional
    public void calibrate(Long strategyId) {
        FundStrategyEntity strategy = requireStrategy(strategyId);
        if (strategy.getStatus() != StrategyParamStatus.PENDING_CALIBRATION) {
            throw new IllegalStateTransitionException(strategy.getStatus().name(), "CALIBRATED");
        }
        // 固定窗口「过去一年」,#11 实现内部对基金成立不满一年自动降级起始日期
        Instant end = Instant.now();
        Instant start = end.minus(365, ChronoUnit.DAYS);
        strategyBacktestService.run(strategyId, new BacktestWindow(start, end));
        strategy.setStatus(StrategyParamStatus.CALIBRATED);
        fundStrategyRepository.save(strategy);
    }

    /**
     * 查某策略版本最近一次回测结果。
     */
    @Transactional(readOnly = true)
    public Optional<StrategyBacktestEntity> getBacktestResult(Long strategyId) {
        return strategyBacktestRepository.findTopByFundStrategyEntity_IdOrderByCreatedDateDesc(strategyId);
    }

    /**
     * 激活:CALIBRATED → EFFECTIVE。
     * <p>校验该版本存在 {@code passed=true} 回测,否则抛 {@link BusinessException}(NO_VALID_BACKTEST)。
     * 同基金旧 EFFECTIVE 自动回退 CALIBRATED;写一行激活表并回填上一任 deactivatedAt。
     */
    @Transactional
    public void activate(Long strategyId) {
        FundStrategyEntity strategy = requireStrategy(strategyId);
        if (strategy.getStatus() != StrategyParamStatus.CALIBRATED) {
            throw new IllegalStateTransitionException(strategy.getStatus().name(), "EFFECTIVE");
        }
        if (!strategyBacktestRepository.existsByFundStrategyEntity_IdAndPassedTrue(strategyId)) {
            throw new BusinessException("NO_VALID_BACKTEST", "策略 " + strategyId + " 无 passed=true 的回测,不可激活");
        }
        // 回退同基金旧 EFFECTIVE
        Long fundId = strategy.getFundEntity().getId();
        fundStrategyRepository.findByFundEntity_IdAndStatus(fundId, StrategyParamStatus.EFFECTIVE)
                .ifPresent(old -> {
                    old.setStatus(StrategyParamStatus.CALIBRATED);
                    fundStrategyRepository.save(old);
                    // 回填旧任期 deactivatedAt
                    fundStrategyActivationRepository
                            .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(old.getId())
                            .ifPresent(act -> {
                                act.setDeactivatedAt(Instant.now());
                                fundStrategyActivationRepository.save(act);
                            });
                });
        // 新版本置 EFFECTIVE + 写激活表
        strategy.setStatus(StrategyParamStatus.EFFECTIVE);
        fundStrategyRepository.save(strategy);
        FundStrategyActivationEntity activation = new FundStrategyActivationEntity();
        activation.setFundEntity(strategy.getFundEntity());
        activation.setFundStrategyEntity(strategy);
        activation.setActivatedAt(Instant.now());
        fundStrategyActivationRepository.save(activation);
    }

    /**
     * 主动停用:EFFECTIVE → CALIBRATED,回填激活表 deactivatedAt。
     * 非 EFFECTIVE 状态抛 {@link IllegalStateTransitionException}。
     */
    @Transactional
    public void retire(Long strategyId) {
        FundStrategyEntity strategy = requireStrategy(strategyId);
        if (strategy.getStatus() != StrategyParamStatus.EFFECTIVE) {
            throw new IllegalStateTransitionException(strategy.getStatus().name(), "CALIBRATED(停用)");
        }
        strategy.setStatus(StrategyParamStatus.CALIBRATED);
        fundStrategyRepository.save(strategy);
        fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(strategyId)
                .ifPresent(act -> {
                    act.setDeactivatedAt(Instant.now());
                    fundStrategyActivationRepository.save(act);
                });
    }

    /**
     * 清仓分水岭(CONTEXT.md):FundEntity.status 从 CLEARED → PENDING_HOLDING 时,
     * 该基金所有策略版本统一回退 PENDING_CALIBRATION,激活表所有未停用任期 deactivatedAt 回填。
     * <p>清仓是分水岭,旧校准和旧回测都不可信。
     */
    @Transactional
    public void onFundClearedToPendingHolding(Long fundId) {
        List<FundStrategyEntity> allVersions = fundStrategyRepository.findByFundEntity_Id(fundId);
        for (FundStrategyEntity strategy : allVersions) {
            strategy.setStatus(StrategyParamStatus.PENDING_CALIBRATION);
            fundStrategyRepository.save(strategy);
        }
        List<FundStrategyActivationEntity> activeActivations =
                fundStrategyActivationRepository.findByFundEntity_IdAndDeactivatedAtIsNull(fundId);
        Instant now = Instant.now();
        for (FundStrategyActivationEntity activation : activeActivations) {
            activation.setDeactivatedAt(now);
            fundStrategyActivationRepository.save(activation);
        }
    }

    private FundStrategyEntity requireStrategy(Long strategyId) {
        return fundStrategyRepository.findById(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("strategy_id=" + strategyId + " 不存在"));
    }

    private void applyRequest(FundStrategyEntity strategy, StrategyConfigRequest request) {
        strategy.setTier1Drawdown(request.tier1Drawdown());
        strategy.setTier2Drawdown(request.tier2Drawdown());
        strategy.setTier3Drawdown(request.tier3Drawdown());
        strategy.setTier4Drawdown(request.tier4Drawdown());
        strategy.setTier1Ratio(request.tier1Ratio());
        strategy.setTier2Ratio(request.tier2Ratio());
        strategy.setTier3Ratio(request.tier3Ratio());
        strategy.setTier4Ratio(request.tier4Ratio());
        strategy.setWeeklyCoolDownThreshold(request.weeklyCoolDownThreshold());
        strategy.setStopLossPullbackPercent(request.stopLossPullbackPercent());
    }
}
