package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.controller.DcaBudgetSummaryView;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.user.service.UserConfigService;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * 月度定投预算的唯一统计口径。预算只提示，不参与任何交易确认或计划生成。
 */
@Service
@RequiredArgsConstructor
public class DcaBudgetSummaryService {

    private final UserConfigService userConfigService;
    private final FundTransactionRepository fundTransactionRepository;
    private final DcaScheduleService dcaScheduleService;
    private final DcaPlanForecastService dcaPlanForecastService;
    private final CurrentActorApi currentActorApi;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DcaBudgetSummaryView currentMonth() {
        Instant now = clock.instant();
        Instant monthStart = dcaScheduleService.startOfCurrentMonth(now);
        Instant monthEnd = dcaScheduleService.startOfNextMonth(now);
        long userId = currentActorApi.userId();
        BigDecimal investedAmount = nonNull(
                fundTransactionRepository.sumAmountByOwnerIdAndSourceAndStatusNotAndTradeDateBetween(
                        userId, FundTransactionSource.INVEST, FundTransactionStatus.CANCELLED, monthStart, monthEnd));

        BigDecimal futureAmount = dcaPlanForecastService.currentMonthRemainingAmount();
        BigDecimal projectedAmount = investedAmount.add(futureAmount);
        BigDecimal monthlyBudget = userConfigService.getMonthlyDcaBudget();
        BigDecimal remainingAmount = null;
        BigDecimal overBudgetAmount = null;
        if (monthlyBudget != null) {
            BigDecimal difference = monthlyBudget.subtract(projectedAmount);
            remainingAmount = difference.signum() >= 0 ? difference : BigDecimal.ZERO;
            overBudgetAmount = difference.signum() < 0 ? difference.negate() : BigDecimal.ZERO;
        }
        return new DcaBudgetSummaryView(monthlyBudget, investedAmount, futureAmount, projectedAmount,
                remainingAmount, overBudgetAmount);
    }

    private BigDecimal nonNull(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
