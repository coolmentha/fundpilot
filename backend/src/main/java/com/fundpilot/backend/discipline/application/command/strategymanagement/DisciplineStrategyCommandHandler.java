package com.fundpilot.backend.discipline.application.command.strategymanagement;

import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisciplineStrategyCommandHandler {
    private final DisciplineStrategyRepository strategies;
    private final StrategyPortfolioFundGateway funds;

    @Transactional
    public Result create(long ownerId, long legacyFundId, Input input) {
        var fund = funds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return createForPortfolioFund(ownerId, fund.id(), input);
    }

    @Transactional
    public Result createForPortfolioFund(long ownerId, long portfolioFundId, Input input) {
        funds.requireTrackedForUpdate(ownerId, portfolioFundId);
        try {
            return from(strategies.save(DisciplineStrategy.create(portfolioFundId, ownerId, input.toDomain())));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.STRATEGY_PARAM_INVALID, exception.getMessage());
        }
    }

    @Transactional
    public Result update(long ownerId, long strategyId, Input input) {
        var value = owned(ownerId, strategyId);
        try {
            value.update(input.toDomain());
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.STRATEGY_PARAM_INVALID, exception.getMessage());
        }
        return from(strategies.save(value));
    }

    @Transactional
    public Result activate(long ownerId, long strategyId) {
        var value = owned(ownerId, strategyId);
        strategies.findEffectiveByPortfolioFundId(value.portfolioFundId()).filter(old -> !old.id().equals(value.id()))
                .ifPresent(old -> {
                    old.retire();
                    strategies.save(old);
                });
        value.activate();
        return from(strategies.save(value));
    }

    @Transactional
    public Result retire(long ownerId, long strategyId) {
        var value = owned(ownerId, strategyId);
        try {
            value.retire();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION, exception.getMessage());
        }
        return from(strategies.save(value));
    }

    private DisciplineStrategy owned(long ownerId, long strategyId) {
        var value = strategies.findById(strategyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STRATEGY_NOT_FOUND, "策略不存在"));
        if (value.ownerId() != ownerId) {
            throw new BusinessException(ErrorCode.STRATEGY_NOT_FOUND, "策略不存在");
        }
        funds.requireTrackedForUpdate(ownerId, value.portfolioFundId());
        return value;
    }

    public static Result from(DisciplineStrategy value) {
        return new Result(value.id(), value.portfolioFundId(), value.ownerId(), value.status().name(), value.activation(),
                value.pullback(), value.harvest(), value.minimumHolding(), value.maxSingleSell(), value.cooldownDays(),
                value.presetCategory(), value.presetVersion(), value.customized(),
                value.takeProfitPhase() == null ? null : value.takeProfitPhase().name());
    }

    public record Input(BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent,
                        BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent,
                        BigDecimal maxSingleSellPercent, Integer cooldownTradingDays, String presetFundCategory,
                        Integer presetVersion, Boolean customized) {
        public Input(BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent,
                     BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent,
                     BigDecimal maxSingleSellPercent, Integer cooldownTradingDays) {
            this(profitActivationPercent, stopLossPullbackPercent, profitHarvestPercent, minimumHoldingPercent,
                    maxSingleSellPercent, cooldownTradingDays, null, null, true);
        }

        DisciplineStrategy.Input toDomain() {
            if (customized == null) {
                throw new IllegalArgumentException("自定义参数标志不能为空");
            }
            return new DisciplineStrategy.Input(profitActivationPercent, stopLossPullbackPercent,
                    profitHarvestPercent, minimumHoldingPercent, maxSingleSellPercent, cooldownTradingDays,
                    presetFundCategory, presetVersion, customized);
        }
    }

    public record Result(Long id, long portfolioFundId, long ownerId, String status,
                         BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent,
                         BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent,
                         BigDecimal maxSingleSellPercent, Integer cooldownTradingDays, String presetFundCategory,
                         Integer presetVersion, boolean customized, String takeProfitPhase) {}
}
