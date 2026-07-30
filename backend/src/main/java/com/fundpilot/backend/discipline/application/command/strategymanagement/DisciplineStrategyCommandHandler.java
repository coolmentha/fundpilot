package com.fundpilot.backend.discipline.application.command.strategymanagement;
import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.strategy.*;
import java.math.BigDecimal; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor public class DisciplineStrategyCommandHandler {
    private final DisciplineStrategyRepository strategies; private final StrategyPortfolioFundGateway funds;
    @Transactional public Result create(long ownerId, long legacyFundId, Input input) {
        var fund = funds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return createForPortfolioFund(ownerId, fund.id(), input);
    }
    @Transactional public Result createForPortfolioFund(long ownerId, long portfolioFundId, Input input) { funds.requireTracked(ownerId, portfolioFundId); return from(strategies.save(DisciplineStrategy.create(portfolioFundId, ownerId, input.toDomain()))); }
    @Transactional public Result update(long ownerId, long strategyId, Input input) { var value = owned(ownerId, strategyId); value.update(input.toDomain()); return from(strategies.save(value)); }
    @Transactional public Result activate(long ownerId, long strategyId) { var value = owned(ownerId, strategyId); strategies.findEffectiveByPortfolioFundId(value.portfolioFundId()).filter(old -> !old.id().equals(value.id())).ifPresent(old -> { old.retire(); strategies.save(old); }); value.activate(); return from(strategies.save(value)); }
    @Transactional public Result retire(long ownerId, long strategyId) { var value = owned(ownerId, strategyId); value.retire(); return from(strategies.save(value)); }
    private DisciplineStrategy owned(long ownerId, long strategyId) { var value = strategies.findById(strategyId).orElseThrow(() -> new Rejected("策略不存在")); if (value.ownerId() != ownerId) throw new Rejected("无权访问策略"); funds.requireTracked(ownerId, value.portfolioFundId()); return value; }
    public static Result from(DisciplineStrategy value) { return new Result(value.id(), value.portfolioFundId(), value.ownerId(), value.status(), value.activation(), value.pullback(), value.harvest(), value.minimumHolding(), value.maxSingleSell(), value.cooldownDays(), value.presetCategory(), value.presetVersion(), value.customized(), value.takeProfitPhase()); }
    public record Input(BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent, BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent, BigDecimal maxSingleSellPercent, Integer cooldownTradingDays, String presetFundCategory, Integer presetVersion, boolean customized) {
        public Input(BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent, BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent, BigDecimal maxSingleSellPercent, Integer cooldownTradingDays) { this(profitActivationPercent, stopLossPullbackPercent, profitHarvestPercent, minimumHoldingPercent, maxSingleSellPercent, cooldownTradingDays, null, null, true); }
        DisciplineStrategy.Input toDomain() { return new DisciplineStrategy.Input(profitActivationPercent, stopLossPullbackPercent, profitHarvestPercent, minimumHoldingPercent, maxSingleSellPercent, cooldownTradingDays, presetFundCategory, presetVersion, customized); }
    }
    public record Result(Long id, long portfolioFundId, long ownerId, String status, BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent, BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent, BigDecimal maxSingleSellPercent, Integer cooldownTradingDays, String presetFundCategory, Integer presetVersion, boolean customized, String takeProfitPhase) {}
    public static final class Rejected extends RuntimeException { public Rejected(String message) { super(message); } }
}
