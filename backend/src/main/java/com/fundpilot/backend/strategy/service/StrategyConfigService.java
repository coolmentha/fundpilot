package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.exception.IllegalStateTransitionException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.service.FundAccessService;
import com.fundpilot.backend.fund.entity.FundStrategyActivationEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundStrategyActivationRepository;
import com.fundpilot.backend.strategy.controller.FundStrategyView;
import com.fundpilot.backend.strategy.controller.StrategyRecommendationView;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 策略参数配置服务:CRUD + 状态机管理。
 *
 * <p>金字塔加仓退场 + 回测/寻优移除后,状态机简化为:
 * <pre>
 * 草稿(PENDING_CALIBRATION) --activate--> EFFECTIVE
 *        |
 *        +--updateDraft(改参数,任意非 EFFECTIVE 态可改)
 * </pre>
 * 不再有 calibrate/CALIBRATED/CALIBRATION_FAILED 流转——回测本身是金字塔寻优配套,金字塔没了回测无意义。
 * PENDING_CALIBRATION 作为"草稿"态(枚举名保留供存量数据兼容),createDraft 后可直接 activate 生效。
 * 同基金同时最多一份 EFFECTIVE(数据库 {@code uq_fund_strategy_effective} 兜底)。
 * activate 新版本时旧 EFFECTIVE 自动回退 PENDING_CALIBRATION;CLEARED→PENDING_HOLDING 时全员回退 PENDING_CALIBRATION。
 */
@Service
@RequiredArgsConstructor
public class StrategyConfigService {
    private final FundAccessService fundAccessService;

    private final FundStrategyRepository fundStrategyRepository;
    private final FundRepository fundRepository;
    private final FundStrategyActivationRepository fundStrategyActivationRepository;
    private final TakeProfitPresetService takeProfitPresetService;

    /**
     * 新建策略草稿。底层继续使用 PENDING_CALIBRATION 枚举值兼容存量数据。
     */
    @Transactional
    public Long createDraft(Long fundId, StrategyConfigRequest request) {
        fundAccessService.requireOwned(fundId);
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setFundEntity(fund);
        strategy.setStatus(StrategyParamStatus.PENDING_CALIBRATION);
        applyRequest(strategy, resolveRequest(fund, request));
        return fundStrategyRepository.save(strategy).getId();
    }

    /**
     * 更新草稿参数——仅 PENDING_CALIBRATION 可改(CALIBRATED/CALIBRATION_FAILED 为存量兼容枚举,按 PENDING 对待),
     * EFFECTIVE 不可改(需先 retire)。否则抛 {@link IllegalStateTransitionException}。
     */
    @Transactional
    public void updateDraft(Long strategyId, StrategyConfigRequest request) {
        FundStrategyEntity strategy = requireStrategy(strategyId);
        if (strategy.getStatus() == StrategyParamStatus.EFFECTIVE) {
            throw new IllegalStateTransitionException(strategy.getStatus().name(), "草稿(非生效态,可改参数)");
        }
        applyRequest(strategy, resolveRequest(strategy.getFundEntity(), request));
        fundStrategyRepository.save(strategy);
    }

    /**
     * 列出某基金所有策略版本。
     */
    @Transactional(readOnly = true)
    public List<FundStrategyEntity> listByFund(Long fundId) {
        fundAccessService.requireOwned(fundId);
        return fundStrategyRepository.findByFundEntity_Id(fundId);
    }

    /** listByFund 的 DTO 包装(供 Controller 用)。 */
    @Transactional(readOnly = true)
    public List<FundStrategyView> listByFundView(Long fundId) {
        return listByFund(fundId).stream().map(FundStrategyView::from).toList();
    }

    /**
     * 查某基金当前 EFFECTIVE 策略(最多一份)。
     */
    @Transactional(readOnly = true)
    public Optional<FundStrategyEntity> findActive(Long fundId) {
        fundAccessService.requireOwned(fundId);
        return fundStrategyRepository.findByFundEntity_IdAndStatus(fundId, StrategyParamStatus.EFFECTIVE);
    }

    /** findActive 的 DTO 包装(供 Controller 用)。 */
    @Transactional(readOnly = true)
    public Optional<FundStrategyView> findActiveView(Long fundId) {
        return findActive(fundId).map(FundStrategyView::from);
    }

    @Transactional(readOnly = true)
    public StrategyRecommendationView recommendation(Long fundId) {
        fundAccessService.requireOwned(fundId);
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        return StrategyRecommendationView.from(takeProfitPresetService.recommend(fund.getFundCategory()));
    }

    /**
     * 激活:草稿(PENDING_CALIBRATION) → EFFECTIVE。
     * <p>金字塔退场后不再要求回测校验——移动止盈阈值无需回测验证。
     * 同基金旧 EFFECTIVE 自动回退 PENDING_CALIBRATION;写一行激活表并回填上一任 deactivatedAt。
     */
    @Transactional
    public void activate(Long strategyId) {
        FundStrategyEntity strategy = requireStrategy(strategyId);
        if (strategy.getStatus() == StrategyParamStatus.EFFECTIVE) {
            return; // 已生效,幂等
        }
        // 回退同基金旧 EFFECTIVE
        Long fundId = strategy.getFundEntity().getId();
        fundStrategyRepository.findByFundEntity_IdAndStatus(fundId, StrategyParamStatus.EFFECTIVE)
                .ifPresent(old -> {
                    old.setStatus(StrategyParamStatus.PENDING_CALIBRATION);
                    clearRuntimeState(old);
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
        initializeRuntimeState(strategy);
        fundStrategyRepository.save(strategy);
        FundStrategyActivationEntity activation = new FundStrategyActivationEntity();
        activation.setFundEntity(strategy.getFundEntity());
        activation.setFundStrategyEntity(strategy);
        activation.setActivatedAt(Instant.now());
        fundStrategyActivationRepository.save(activation);
    }

    /**
     * 主动停用:EFFECTIVE → 草稿(PENDING_CALIBRATION),回填激活表 deactivatedAt。
     * 非 EFFECTIVE 状态抛 {@link IllegalStateTransitionException}。
     */
    @Transactional
    public void retire(Long strategyId) {
        FundStrategyEntity strategy = requireStrategy(strategyId);
        if (strategy.getStatus() != StrategyParamStatus.EFFECTIVE) {
            throw new IllegalStateTransitionException(strategy.getStatus().name(), "已生效策略");
        }
        strategy.setStatus(StrategyParamStatus.PENDING_CALIBRATION);
        clearRuntimeState(strategy);
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
     */
    @Transactional
    public void onFundClearedToPendingHolding(Long fundId) {
        List<FundStrategyEntity> allVersions = fundStrategyRepository.findByFundEntity_Id(fundId);
        for (FundStrategyEntity strategy : allVersions) {
            strategy.setStatus(StrategyParamStatus.PENDING_CALIBRATION);
            clearRuntimeState(strategy);
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
        FundStrategyEntity strategy = fundStrategyRepository.findById(strategyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STRATEGY_NOT_FOUND, "FundStrategy #" + strategyId + " 不存在"));
        fundAccessService.requireOwned(strategy.getFundEntity());
        return strategy;
    }

    private StrategyConfigRequest resolveRequest(FundEntity fund, StrategyConfigRequest request) {
        TakeProfitPreset preset = takeProfitPresetService.recommend(fund.getFundCategory());
        StrategyConfigRequest resolved = takeProfitPresetService.fillMissing(request, preset);
        validateRequest(resolved);
        return resolved;
    }

    private void applyRequest(FundStrategyEntity strategy, StrategyConfigRequest request) {
        TakeProfitPreset preset = takeProfitPresetService.recommend(strategy.getFundEntity().getFundCategory());
        strategy.setProfitActivationPercent(request.profitActivationPercent());
        strategy.setStopLossPullbackPercent(request.stopLossPullbackPercent());
        strategy.setProfitHarvestPercent(request.profitHarvestPercent());
        strategy.setMinimumHoldingPercent(request.minimumHoldingPercent());
        strategy.setMaxSingleSellPercent(request.maxSingleSellPercent());
        strategy.setCooldownTradingDays(request.cooldownTradingDays());
        strategy.setPresetFundCategory(preset.fundCategory());
        strategy.setPresetVersion(preset.presetVersion());
        strategy.setCustomized(takeProfitPresetService.isCustomized(request, preset));
    }

    private static void validateRequest(StrategyConfigRequest request) {
        requireRange("止盈启动收益率", request.profitActivationPercent(), false, true);
        requireRange("高点回撤比例", request.stopLossPullbackPercent(), false, false);
        requireRange("浮盈收割比例", request.profitHarvestPercent(), false, true);
        requireRange("最低保留仓位", request.minimumHoldingPercent(), true, false);
        requireRange("单次最大卖出比例", request.maxSingleSellPercent(), false, true);
        if (request.cooldownTradingDays() == null
                || request.cooldownTradingDays() < 0
                || request.cooldownTradingDays() > 250) {
            throw ErrorCode.STRATEGY_PARAM_INVALID.toException("冷静期交易日必须在 0 到 250 之间");
        }
    }

    private static void requireRange(String name, BigDecimal value, boolean allowZero, boolean allowOne) {
        if (value == null) {
            throw ErrorCode.STRATEGY_PARAM_INVALID.toException(name + "不能为空");
        }
        int zero = value.compareTo(BigDecimal.ZERO);
        int one = value.compareTo(BigDecimal.ONE);
        if ((allowZero ? zero < 0 : zero <= 0) || (allowOne ? one > 0 : one >= 0)) {
            throw ErrorCode.STRATEGY_PARAM_INVALID.toException(name + "取值范围非法: " + value);
        }
    }

    private static void initializeRuntimeState(FundStrategyEntity strategy) {
        clearRuntimeState(strategy);
        strategy.setTakeProfitPhase(TakeProfitPhase.ACCUMULATING);
    }

    private static void clearRuntimeState(FundStrategyEntity strategy) {
        strategy.setTakeProfitPhase(null);
        strategy.setCycleStartedAt(null);
        strategy.setCyclePeakNav(null);
        strategy.setTriggeredSignalId(null);
        strategy.setCooldownStartedAt(null);
    }
}
