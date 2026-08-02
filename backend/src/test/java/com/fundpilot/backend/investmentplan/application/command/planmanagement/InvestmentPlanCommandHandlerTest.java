package com.fundpilot.backend.investmentplan.application.command.planmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.application.command.planmanagement.InvestmentPlanCommandHandler.PlanInput;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanStatus;
import com.fundpilot.backend.platform.web.error.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InvestmentPlanCommandHandlerTest {
    @ParameterizedTest
    @MethodSource("invalidInputs")
    void 创建时非法参数返回DcaPlanInvalid(PlanInput input) {
        var plans = mock(InvestmentPlanRepository.class);
        var funds = trackedFunds();
        when(plans.findEffectiveByPortfolioFundId(11L)).thenReturn(Optional.empty());
        var handler = new InvestmentPlanCommandHandler(plans, funds);

        assertThatThrownBy(() -> handler.createForPortfolioFund(3L, 11L, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("DCA_PLAN_INVALID"));
        verify(plans, never()).save(any(InvestmentPlan.class));
    }

    @Test
    void 更新时非法金额返回DcaPlanInvalid() {
        var plans = mock(InvestmentPlanRepository.class);
        var funds = trackedFunds();
        when(plans.findById(7L)).thenReturn(Optional.of(plan(InvestmentPlanStatus.EFFECTIVE)));
        var handler = new InvestmentPlanCommandHandler(plans, funds);

        assertThatThrownBy(() -> handler.update(3L, 7L, input(BigDecimal.ZERO, "DAILY", null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("DCA_PLAN_INVALID"));
        verify(plans, never()).save(any(InvestmentPlan.class));
    }

    @Test
    void DRAFT计划退休返回IllegalStateTransition() {
        var plans = mock(InvestmentPlanRepository.class);
        var funds = trackedFunds();
        when(plans.findById(7L)).thenReturn(Optional.of(plan(InvestmentPlanStatus.DRAFT)));
        var handler = new InvestmentPlanCommandHandler(plans, funds);

        assertThatThrownBy(() -> handler.retire(3L, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void DRAFT计划暂停或恢复返回IllegalStateTransition() {
        var plans = mock(InvestmentPlanRepository.class);
        var funds = trackedFunds();
        when(plans.findById(7L)).thenReturn(Optional.of(plan(InvestmentPlanStatus.DRAFT)));
        var handler = new InvestmentPlanCommandHandler(plans, funds);

        assertThatThrownBy(() -> handler.setEnabled(3L, 7L, true))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    private static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(input(BigDecimal.ZERO, "DAILY", null, null)),
                Arguments.of(input(new BigDecimal("100"), "WEEKLY", 6, null)),
                Arguments.of(input(new BigDecimal("100"), "MONTHLY", null, 29)));
    }

    private static PlanPortfolioFundGateway trackedFunds() {
        var funds = mock(PlanPortfolioFundGateway.class);
        when(funds.requireTracked(3L, 11L))
                .thenReturn(new PlanPortfolioFundGateway.PortfolioFund(11L, 41L));
        return funds;
    }

    private static InvestmentPlan plan(InvestmentPlanStatus status) {
        return InvestmentPlan.rehydrate(7L, 17L, 11L, 3L, true, new BigDecimal("100"),
                InvestmentPlanFrequency.DAILY, null, null, status);
    }

    private static PlanInput input(BigDecimal amount, String frequency, Integer dayOfWeek, Integer dayOfMonth) {
        return new PlanInput(true, amount, frequency, dayOfWeek, dayOfMonth);
    }
}
