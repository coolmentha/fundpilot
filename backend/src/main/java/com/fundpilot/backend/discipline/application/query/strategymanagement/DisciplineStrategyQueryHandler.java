package com.fundpilot.backend.discipline.application.query.strategymanagement;
import com.fundpilot.backend.discipline.application.command.strategymanagement.DisciplineStrategyCommandHandler;
import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationRepository;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyPreset;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import java.util.List; import java.util.Set; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor public class DisciplineStrategyQueryHandler {
    private final DisciplineStrategyRepository strategies; private final StrategyPortfolioFundGateway funds;
    private final DisciplineClassificationRepository classifications;
    @Transactional(readOnly = true) public List<DisciplineStrategyCommandHandler.Result> listByLegacyFund(long ownerId, long legacyFundId) { var fund = funds.requireTrackedByLegacyFund(ownerId, legacyFundId); return strategies.findByPortfolioFundId(fund.id()).stream().map(DisciplineStrategyCommandHandler::from).toList(); }
    @Transactional(readOnly = true) public List<DisciplineStrategyCommandHandler.Result> listByPortfolioFund(long ownerId, long portfolioFundId) { funds.requireTracked(ownerId, portfolioFundId); return strategies.findByPortfolioFundId(portfolioFundId).stream().map(DisciplineStrategyCommandHandler::from).toList(); }
    @Transactional(readOnly = true) public DisciplineStrategyCommandHandler.Result activeByLegacyFund(long ownerId, long legacyFundId) { var fund = funds.requireTrackedByLegacyFund(ownerId, legacyFundId); return strategies.findEffectiveByPortfolioFundId(fund.id()).map(DisciplineStrategyCommandHandler::from).orElse(null); }
    @Transactional(readOnly = true) public DisciplineStrategyCommandHandler.Result activeByPortfolioFund(long ownerId, long portfolioFundId) { funds.requireTracked(ownerId, portfolioFundId); return strategies.findEffectiveByPortfolioFundId(portfolioFundId).map(DisciplineStrategyCommandHandler::from).orElse(null); }
    @Transactional(readOnly = true) public List<DisciplineStrategyCommandHandler.Result> effective() { return strategies.findEffective().stream().map(DisciplineStrategyCommandHandler::from).toList(); }
    @Transactional(readOnly = true) public Recommendation recommendation(long ownerId, long legacyFundId) {
        var fund = funds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return recommendationForPortfolioFund(ownerId, fund.id());
    }
    @Transactional(readOnly = true) public Recommendation recommendationByPortfolioFund(long ownerId, long portfolioFundId) {
        funds.requireTracked(ownerId, portfolioFundId);
        return recommendationForPortfolioFund(ownerId, portfolioFundId);
    }
    private Recommendation recommendationForPortfolioFund(long ownerId, long portfolioFundId) {
        var classification = classifications.findByPortfolioFundIds(ownerId, Set.of(portfolioFundId)).stream()
                .findFirst().orElseThrow(() -> new IllegalStateException("纪律分类不存在: " + portfolioFundId));
        var preset = DisciplineStrategyPreset.recommend(classification.category());
        return new Recommendation(preset.category().name(), preset.version(), preset.profitActivationPercent(),
                preset.stopLossPullbackPercent(), preset.profitHarvestPercent(), preset.minimumHoldingPercent(),
                preset.maxSingleSellPercent(), preset.cooldownTradingDays());
    }
    public record Recommendation(String category, int version, java.math.BigDecimal profitActivationPercent,
                                 java.math.BigDecimal stopLossPullbackPercent,
                                 java.math.BigDecimal profitHarvestPercent,
                                 java.math.BigDecimal minimumHoldingPercent,
                                 java.math.BigDecimal maxSingleSellPercent, int cooldownTradingDays) {}
}
