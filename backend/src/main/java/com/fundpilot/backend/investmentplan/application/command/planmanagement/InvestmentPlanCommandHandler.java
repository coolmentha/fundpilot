package com.fundpilot.backend.investmentplan.application.command.planmanagement;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
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
        portfolioFunds.requireTracked(ownerId, portfolioFundId);
        InvestmentPlan plan;
        try {
            plan = InvestmentPlan.create(portfolioFundId, ownerId, input.enabled(), input.amount(),
                    frequency(input.frequency()), input.dayOfWeek(), input.dayOfMonth());
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
            plan.update(input.enabled(), input.amount(), frequency(input.frequency()), input.dayOfWeek(), input.dayOfMonth());
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

    public static PlanResult from(InvestmentPlan plan) {
        return new PlanResult(plan.id(), plan.portfolioFundId(), plan.ownerId(), plan.enabled(), plan.amount(),
                plan.frequency().name(), plan.dayOfWeek(), plan.dayOfMonth(), plan.status().name(),
                plan.createdDate(), List.of());
    }
    public record PlanInput(boolean enabled, BigDecimal amount, String frequency, Integer dayOfWeek,
                            Integer dayOfMonth) {}
    public record PlanResult(Long id, long portfolioFundId, long ownerId, boolean enabled, BigDecimal amount,
                             String frequency, Integer dayOfWeek, Integer dayOfMonth, String status,
                             Instant createdDate, List<Instant> remainingExecutionDates) {
        public PlanResult withForecast(List<Instant> executionDates) {
            return new PlanResult(id, portfolioFundId, ownerId, enabled, amount, frequency, dayOfWeek, dayOfMonth,
                    status, createdDate, List.copyOf(executionDates));
        }
    }
}
