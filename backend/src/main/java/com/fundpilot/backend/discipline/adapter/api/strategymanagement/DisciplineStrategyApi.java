package com.fundpilot.backend.discipline.adapter.api.strategymanagement;

import com.fundpilot.backend.discipline.application.command.strategymanagement.DisciplineStrategyCommandHandler;
import com.fundpilot.backend.discipline.application.query.strategymanagement.DisciplineStrategyQueryHandler;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DisciplineStrategyApi {
    private final DisciplineStrategyCommandHandler commands;
    private final DisciplineStrategyQueryHandler queries;

    public List<EffectiveStrategy> effectiveStrategies() {
        return queries.effective().stream().map(DisciplineStrategyApi::from).toList();
    }

    private static EffectiveStrategy from(DisciplineStrategyCommandHandler.Result value) {
        return new EffectiveStrategy(value.id(), value.portfolioFundId(), value.ownerId(),
                value.profitActivationPercent(), value.stopLossPullbackPercent(), value.profitHarvestPercent(),
                value.minimumHoldingPercent(), value.maxSingleSellPercent(), value.cooldownTradingDays(),
                value.presetFundCategory(), value.presetVersion(), value.customized());
    }

    public record EffectiveStrategy(long id, long portfolioFundId, long ownerId,
                                    BigDecimal profitActivationPercent, BigDecimal stopLossPullbackPercent,
                                    BigDecimal profitHarvestPercent, BigDecimal minimumHoldingPercent,
                                    BigDecimal maxSingleSellPercent, Integer cooldownTradingDays,
                                    String presetFundCategory, Integer presetVersion, boolean customized) {
    }
}
