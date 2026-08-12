package com.fundpilot.backend.investmentplan.infrastructure.gateway.planmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanPortfolioFundGatewayImplTest {
    @Test
    void legacyFund入口将作废基金映射为业务错误() {
        PortfolioFundApi funds = mock(PortfolioFundApi.class);
        when(funds.findOwnedByLegacyFundId(3L, 41L)).thenReturn(Optional.of(voidedFund()));

        assertVoided(() -> gateway(funds)
                .requireTrackedByLegacyFund(3L, 41L));
    }

    @Test
    void portfolioFund入口将作废基金映射为业务错误() {
        PortfolioFundApi funds = mock(PortfolioFundApi.class);
        when(funds.findOwned(3L, 7L)).thenReturn(Optional.of(voidedFund()));

        assertVoided(() -> gateway(funds).requireTracked(3L, 7L));
    }

    @Test
    void owner查询只返回tracked组合基金() {
        PortfolioFundApi funds = mock(PortfolioFundApi.class);
        when(funds.findByOwner(3L)).thenReturn(List.of(trackedFund(), voidedFund()));

        var result = gateway(funds).findTrackedByOwner(3L);

        assertThat(result).extracting(PlanPortfolioFundGateway.PortfolioFund::id)
                .containsExactly(7L);
    }

    @Test
    void 执行前锁定组合基金并返回tracked基金() {
        PortfolioFundApi funds = mock(PortfolioFundApi.class);
        when(funds.findForUpdate(7L)).thenReturn(Optional.of(trackedFund()));

        var result = gateway(funds).findTrackedForExecution(3L, 7L);

        assertThat(result).contains(new PlanPortfolioFundGateway.PortfolioFund(7L, 41L));
        verify(funds).findForUpdate(7L);
    }

    private static void assertVoided(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("ILLEGAL_STATE_TRANSITION");
                    assertThat(exception).hasMessageContaining("作废组合基金");
                });
    }

    private static PortfolioFundApi.PortfolioFund voidedFund() {
        return new PortfolioFundApi.PortfolioFund(7L, 41L, 3L, 101L,
                PortfolioFundApi.Validity.VOIDED, true, new BigDecimal("0.30"), null, 9L, "录入错误");
    }

    private static PortfolioFundApi.PortfolioFund trackedFund() {
        return new PortfolioFundApi.PortfolioFund(7L, 41L, 3L, 101L,
                PortfolioFundApi.Validity.TRACKED, true, new BigDecimal("0.30"), null, null, null);
    }

    private static PlanPortfolioFundGatewayImpl gateway(PortfolioFundApi funds) {
        return new PlanPortfolioFundGatewayImpl(funds, mock(FundProductApi.class));
    }
}
