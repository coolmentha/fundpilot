package com.fundpilot.backend.investmentplan.infrastructure.gateway.planexecution;

import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanInvestmentFactsGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import com.fundpilot.backend.investmentplan.domain.investmentplan.SmartInvestmentAmountPolicy;
import com.fundpilot.backend.marketdata.adapter.api.indexkline.IndexKlineApi;
import com.fundpilot.backend.marketdata.adapter.api.indexvaluation.IndexValuationApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PlanInvestmentFactsGatewayImpl implements PlanInvestmentFactsGateway {
    private static final String VALUATION_SOURCE = "CSINDEX_INDEX_CSI_DS_PE_PEG";
    private final PositionApi positions;
    private final PublishedNavApi navs;
    private final IndexKlineApi klines;
    private final IndexValuationApi valuations;

    @Override
    public Optional<Facts> load(InvestmentPlan plan, PlanPortfolioFundGateway.PortfolioFund fund,
                                Instant businessDate) {
        String indexCode = plan.referenceIndexCode() == null ? fund.benchmarkIndexCode()
                : plan.referenceIndexCode();
        return switch (plan.amountStrategy()) {
            case FIXED -> Optional.of(new Facts(SmartInvestmentAmountPolicy.Facts.empty(), null, indexCode, null));
            case LOW_VALUATION -> valuation(indexCode, businessDate);
            case MOVING_AVERAGE -> movingAverage(indexCode, plan.movingAverageDays(), businessDate);
            case CHANGE_RATE -> changeRate(plan.ownerId(), fund.id(), fund.fundProductId(), businessDate);
        };
    }

    private Optional<Facts> valuation(String indexCode, Instant businessDate) {
        if (indexCode == null || indexCode.isBlank()) return Optional.empty();
        List<IndexValuationApi.Valuation> history = valuations.history(indexCode, VALUATION_SOURCE, businessDate);
        if (history.isEmpty()) return Optional.empty();
        var latest = history.getLast();
        long below = history.stream().filter(value -> value.peRatio().compareTo(latest.peRatio()) <= 0).count();
        BigDecimal percentile = BigDecimal.valueOf(below).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(history.size()), 8, java.math.RoundingMode.HALF_UP);
        return Optional.of(new Facts(new SmartInvestmentAmountPolicy.Facts(percentile, null, null, null, null, null),
                latest.tradeDate(), indexCode, null));
    }

    private Optional<Facts> movingAverage(String indexCode, Integer days, Instant businessDate) {
        if (indexCode == null || indexCode.isBlank()) return Optional.empty();
        int window = days == null ? 250 : days;
        List<IndexKlineApi.Bar> history = klines.findAll(indexCode).stream()
                .filter(value -> value.tradeDate().isBefore(businessDate))
                .filter(value -> value.close() != null && value.close().signum() > 0)
                .sorted(Comparator.comparing(IndexKlineApi.Bar::tradeDate)).toList();
        if (history.size() < window || history.size() < 10) return Optional.empty();
        List<IndexKlineApi.Bar> windowBars = history.subList(history.size() - window, history.size());
        BigDecimal average = windowBars.stream().map(IndexKlineApi.Bar::close).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window), 12, java.math.RoundingMode.HALF_UP);
        List<IndexKlineApi.Bar> recent = history.subList(history.size() - 10, history.size());
        BigDecimal high = recent.stream().map(IndexKlineApi.Bar::close).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal low = recent.stream().map(IndexKlineApi.Bar::close).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal amplitude = high.divide(low, 12, java.math.RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        var latest = history.getLast();
        return Optional.of(new Facts(new SmartInvestmentAmountPolicy.Facts(null, latest.close(), average,
                amplitude, null, null), latest.tradeDate(), indexCode, window));
    }

    private Optional<Facts> changeRate(long ownerId, long portfolioFundId, Long productId, Instant businessDate) {
        var history = productId == null ? List.<PublishedNavApi.PublishedNav>of()
                : navs.history(productId, Instant.EPOCH, businessDate);
        var nav = history.isEmpty() ? Optional.<PublishedNavApi.PublishedNav>empty()
                : Optional.of(history.getLast());
        var position = positions.findOwned(ownerId, portfolioFundId);
        return Optional.of(new Facts(new SmartInvestmentAmountPolicy.Facts(null, null, null, null,
                nav.map(PublishedNavApi.PublishedNav::unitNav).orElse(null),
                position.map(PositionApi.Position::costPerShare).orElse(null)),
                nav.map(PublishedNavApi.PublishedNav::navDate).orElse(null), null, null));
    }
}
