package com.fundpilot.backend.discipline.application.query.advicequery;

import com.fundpilot.backend.discipline.application.gateway.advicegeneration.GeneratedAdvicePortfolioGateway;
import com.fundpilot.backend.discipline.application.gateway.adviceresponse.AdviceTransactionGateway;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.Advice;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdviceQueryHandler {
    private final AdviceRepository advice; private final GeneratedAdvicePortfolioGateway portfolioFunds;
    private final AdviceTransactionGateway transactions;
    @Transactional(readOnly = true) public List<Result> pending(long ownerId) {
        var funds = portfolioFunds.findTrackedByOwner(ownerId);
        return advice.findPendingByOwner(ownerId).stream().filter(value -> value.action() != AdviceAction.NONE)
                .filter(value -> !transactions.hasTransaction(value.id())).flatMap(value -> funds.stream()
                .filter(fund -> fund.id() == value.portfolioFundId())
                .map(fund -> from(value, fund.legacyFundId()))).toList();
    }
    @Transactional(readOnly = true) public Result latest(long ownerId, long legacyFundId) {
        long fundId = portfolioFunds.requireTracked(ownerId, legacyFundId).id();
        return advice.findLatestByPortfolioFund(fundId).map(value -> from(value, legacyFundId)).orElse(null);
    }
    @Transactional(readOnly = true) public Result latestByPortfolioFund(long ownerId, long portfolioFundId) {
        portfolioFunds.requireTrackedByPortfolioFundId(ownerId, portfolioFundId);
        return advice.findLatestByPortfolioFund(portfolioFundId).map(value -> from(value, null)).orElse(null);
    }
    @Transactional(readOnly = true) public List<Result> range(long ownerId, long legacyFundId, Instant from, Instant to) {
        long fundId = portfolioFunds.requireTracked(ownerId, legacyFundId).id();
        return advice.findByPortfolioFundAndSignalDateBetween(fundId, from, to).stream().map(value -> from(value, legacyFundId)).toList();
    }
    @Transactional(readOnly = true) public List<Result> rangeByPortfolioFund(long ownerId, long portfolioFundId, Instant from, Instant to) {
        portfolioFunds.requireTrackedByPortfolioFundId(ownerId, portfolioFundId);
        return advice.findByPortfolioFundAndSignalDateBetween(portfolioFundId, from, to).stream().map(value -> from(value, null)).toList();
    }
    private Result from(Advice value, Long legacyFundId) {
        var related = transactions.relatedTransaction(value.id()).orElse(null);
        return new Result(value.id(), legacyFundId, value.portfolioFundId(), value.action().name(),
                value.responseStatus().name(), value.signalDate(), value.triggerTier(), value.coefficient(),
                value.suggestedValue(), value.suggestedMeasureUnit(), value.reason(), value.warnings(),
                related == null ? null : related.transactionId(),
                related == null ? null : related.status());
    }
    public record Result(long id, Long legacyFundId, long portfolioFundId, String action, String responseStatus,
                         Instant signalDate, Integer triggerTier, BigDecimal coefficient, BigDecimal suggestedValue,
                         String suggestedMeasureUnit, String reason, String warnings, Long relatedTransactionId,
                         String relatedTransactionStatus) {}
}
