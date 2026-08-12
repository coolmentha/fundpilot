package com.fundpilot.backend.investmentplan.application.command.planmanagement;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentPlanCommandHandler {
    private final InvestmentPlanRepository plans;
    private final PlanPortfolioFundGateway portfolioFunds;

    @Transactional
    public PlanResult create(long ownerId, long legacyFundId, PlanInput input) {
        var fund = portfolioFunds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return createForPortfolioFund(ownerId, fund.id(), input);
    }

    @Transactional
    public PlanResult createForPortfolioFund(long ownerId, long portfolioFundId, PlanInput input) {
        var tracked = portfolioFunds.requireTracked(ownerId, portfolioFundId);
        InvestmentPlan plan;
        try {
            var config = config(input, tracked);
            plan = InvestmentPlan.create(portfolioFundId, ownerId, input.enabled(), input.amount(),
                    frequency(input.frequency()), input.dayOfWeek(), input.dayOfMonth(), config.strategy(),
                    config.referenceIndexCode(), config.movingAverageDays());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, exception.getMessage());
        }
        plans.findEffectiveByPortfolioFundId(portfolioFundId).ifPresent(existing -> {
            existing.retire();
            plans.save(existing);
        });
        return from(plans.save(plan));
    }

    @Transactional
    public PlanResult update(long ownerId, long planId, PlanInput input) {
        InvestmentPlan plan = owned(ownerId, planId);
        try {
            var config = config(input, portfolioFunds.requireTracked(ownerId, plan.portfolioFundId()));
            plan.update(input.enabled(), input.amount(), frequency(input.frequency()), input.dayOfWeek(),
                    input.dayOfMonth(), config.strategy(), config.referenceIndexCode(), config.movingAverageDays());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, exception.getMessage());
        }
        return from(plans.save(plan));
    }

    @Transactional
    public PlanResult activate(long ownerId, long planId) {
        InvestmentPlan plan = owned(ownerId, planId);
        plans.findEffectiveByPortfolioFundId(plan.portfolioFundId()).filter(other -> !other.id().equals(plan.id()))
                .ifPresent(other -> { other.retire(); plans.save(other); });
        plan.activate();
        return from(plans.save(plan));
    }

    @Transactional
    public PlanResult retire(long ownerId, long planId) {
        InvestmentPlan plan = owned(ownerId, planId);
        try {
            plan.retire();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION, exception.getMessage());
        }
        return from(plans.save(plan));
    }

    @Transactional
    public PlanResult setEnabled(long ownerId, long planId, boolean enabled) {
        InvestmentPlan plan = owned(ownerId, planId);
        try {
            plan.setEnabled(enabled);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION, exception.getMessage());
        }
        return from(plans.save(plan));
    }

    @Transactional
    public void delete(long ownerId, long planId) {
        InvestmentPlan plan = owned(ownerId, planId);
        if (plan.status() != InvestmentPlanStatus.DRAFT) {
            throw new BusinessException(ErrorCode.DCA_PLAN_DELETE_REQUIRES_DRAFT, "请先停用定投计划再删除");
        }
        plans.delete(plan);
    }

    private InvestmentPlan owned(long ownerId, long planId) {
        InvestmentPlan plan = plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DCA_PLAN_NOT_FOUND, "定投计划不存在"));
        if (plan.ownerId() != ownerId) {
            throw new BusinessException(ErrorCode.DCA_PLAN_NOT_FOUND, "定投计划不存在");
        }
        portfolioFunds.requireTracked(ownerId, plan.portfolioFundId());
        return plan;
    }

    private static InvestmentPlanFrequency frequency(String value) {
        try {
            return InvestmentPlanFrequency.valueOf(value);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "不支持的定投频率");
        }
    }

    private static PlanConfig config(PlanInput input, PlanPortfolioFundGateway.PortfolioFund fund) {
        InvestmentPlanAmountStrategy strategy;
        try {
            strategy = input.amountStrategy() == null || input.amountStrategy().isBlank()
                    ? InvestmentPlanAmountStrategy.FIXED : InvestmentPlanAmountStrategy.valueOf(input.amountStrategy());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "不支持的金额策略");
        }
        String reference = switch (strategy) {
            case LOW_VALUATION -> fund.benchmarkIndexCode();
            case MOVING_AVERAGE -> input.referenceIndexCode() == null || input.referenceIndexCode().isBlank()
                    ? fund.benchmarkIndexCode() : input.referenceIndexCode().trim();
            case FIXED, CHANGE_RATE -> null;
        };
        if ((strategy == InvestmentPlanAmountStrategy.LOW_VALUATION
                || strategy == InvestmentPlanAmountStrategy.MOVING_AVERAGE)
                && (reference == null || reference.isBlank())) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "该策略需要基金基准指数");
        }
        Integer days = strategy == InvestmentPlanAmountStrategy.MOVING_AVERAGE
                ? input.movingAverageDays() == null ? 250 : input.movingAverageDays() : null;
        return new PlanConfig(strategy, reference, days);
    }

    public static PlanResult from(InvestmentPlan plan) {
        BigDecimal minimumRate = switch (plan.amountStrategy()) {
            case LOW_VALUATION -> BigDecimal.ZERO;
            case MOVING_AVERAGE -> new BigDecimal("0.60");
            case CHANGE_RATE -> new BigDecimal("0.50");
            case FIXED -> BigDecimal.ONE;
        };
        BigDecimal maximumRate = switch (plan.amountStrategy()) {
            case LOW_VALUATION -> BigDecimal.ONE;
            case MOVING_AVERAGE -> new BigDecimal("2.10");
            case CHANGE_RATE -> new BigDecimal("2.00");
            case FIXED -> BigDecimal.ONE;
        };
        return new PlanResult(plan.id(), plan.portfolioFundId(), plan.ownerId(), plan.enabled(), plan.amount(),
                plan.frequency().name(), plan.dayOfWeek(), plan.dayOfMonth(), plan.status().name(),
                plan.amountStrategy().name(), plan.referenceIndexCode(), plan.movingAverageDays(),
                plan.amount().multiply(minimumRate), plan.amount().multiply(maximumRate),
                plan.createdDate(), List.of(), null);
    }
    public record PlanInput(boolean enabled, BigDecimal amount, String frequency, Integer dayOfWeek,
                            Integer dayOfMonth, String amountStrategy, String referenceIndexCode,
                            Integer movingAverageDays) {
        public PlanInput(boolean enabled, BigDecimal amount, String frequency, Integer dayOfWeek,
                         Integer dayOfMonth) {
            this(enabled, amount, frequency, dayOfWeek, dayOfMonth, "FIXED", null, null);
        }
    }
    public record PlanResult(Long id, long portfolioFundId, long ownerId, boolean enabled, BigDecimal amount,
                             String frequency, Integer dayOfWeek, Integer dayOfMonth, String status,
                             String amountStrategy, String referenceIndexCode, Integer movingAverageDays,
                             BigDecimal minimumAmount, BigDecimal maximumAmount, Instant createdDate,
                             List<Instant> remainingExecutionDates, LatestDecision latestDecision) {
        public PlanResult(Long id, long portfolioFundId, long ownerId, boolean enabled, BigDecimal amount,
                          String frequency, Integer dayOfWeek, Integer dayOfMonth, String status,
                          Instant createdDate, List<Instant> remainingExecutionDates) {
            this(id, portfolioFundId, ownerId, enabled, amount, frequency, dayOfWeek, dayOfMonth, status,
                    "FIXED", null, null, amount, amount, createdDate, remainingExecutionDates, null);
        }
        public PlanResult withForecast(List<Instant> executionDates) {
            return new PlanResult(id, portfolioFundId, ownerId, enabled, amount, frequency, dayOfWeek, dayOfMonth,
                    status, amountStrategy, referenceIndexCode, movingAverageDays, minimumAmount, maximumAmount,
                    createdDate, List.copyOf(executionDates), latestDecision);
        }
        public PlanResult withLatestDecision(LatestDecision decision) {
            return new PlanResult(id, portfolioFundId, ownerId, enabled, amount, frequency, dayOfWeek, dayOfMonth,
                    status, amountStrategy, referenceIndexCode, movingAverageDays, minimumAmount, maximumAmount,
                    createdDate, remainingExecutionDates, decision);
        }
    }
    public record LatestDecision(String result, BigDecimal actualAmount, BigDecimal deductionRate,
                                 String ruleVersion, Instant dataDate, String reasonCode, String reason) {}
    private record PlanConfig(InvestmentPlanAmountStrategy strategy, String referenceIndexCode,
                              Integer movingAverageDays) {}
}
