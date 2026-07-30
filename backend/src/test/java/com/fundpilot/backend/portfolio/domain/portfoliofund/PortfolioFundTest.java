package com.fundpilot.backend.portfolio.domain.portfoliofund;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioFundTest {
    private static final Instant FIRST_VOID_TIME = Instant.parse("2026-07-26T08:00:00Z");

    @Test
    void voidsTrackedPortfolioFundAndRecordsCompleteAudit() {
        PortfolioFund portfolioFund = trackedPortfolioFund();

        PortfolioFundVoided event = portfolioFund.voidBy(7L, "基金代码录入错误", FIRST_VOID_TIME)
                .orElseThrow();

        assertThat(portfolioFund.validity()).isEqualTo(PortfolioFundValidity.VOIDED);
        assertThat(portfolioFund.voidedAt()).isEqualTo(FIRST_VOID_TIME);
        assertThat(portfolioFund.voidedBy()).isEqualTo(7L);
        assertThat(portfolioFund.voidReason()).isEqualTo("基金代码录入错误");
        assertThat(event).isEqualTo(new PortfolioFundVoided(
                11L, 3L, 5L, 7L, "基金代码录入错误", FIRST_VOID_TIME));
    }

    @Test
    void rejectsBlankVoidReasonWithoutChangingState() {
        PortfolioFund portfolioFund = trackedPortfolioFund();

        assertThatThrownBy(() -> portfolioFund.voidBy(7L, "  ", FIRST_VOID_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("作废原因不能为空");

        assertThat(portfolioFund.validity()).isEqualTo(PortfolioFundValidity.TRACKED);
        assertThat(portfolioFund.voidedAt()).isNull();
    }

    @Test
    void repeatedVoidIsIdempotentAndKeepsFirstAudit() {
        PortfolioFund portfolioFund = trackedPortfolioFund();
        portfolioFund.voidBy(7L, "基金代码录入错误", FIRST_VOID_TIME);

        assertThat(portfolioFund.voidBy(0L, " ", null)).isEmpty();

        assertThat(portfolioFund.voidedAt()).isEqualTo(FIRST_VOID_TIME);
        assertThat(portfolioFund.voidedBy()).isEqualTo(7L);
        assertThat(portfolioFund.voidReason()).isEqualTo("基金代码录入错误");
    }

    @Test
    void rejectsIncompleteVoidAuditDuringRehydration() {
        assertThatThrownBy(() -> PortfolioFund.rehydrate(
                11L, 101L, 3L, 5L, PortfolioFundValidity.VOIDED,
                true, new BigDecimal("0.30"), FIRST_VOID_TIME, null, "历史作废"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("作废组合基金必须包含完整审计");
    }

    @Test
    void updatesWarningOnlyWhileTracked() {
        PortfolioFund portfolioFund = trackedPortfolioFund();

        portfolioFund.configurePositionWarning(false, new BigDecimal("0.25"));

        assertThat(portfolioFund.positionWarningEnabled()).isFalse();
        assertThat(portfolioFund.positionWarningRatio()).isEqualByComparingTo("0.25");
        portfolioFund.voidBy(7L, "录入错误", FIRST_VOID_TIME);
        assertThatThrownBy(() -> portfolioFund.configurePositionWarning(true, new BigDecimal("0.20")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("作废组合基金不能修改仓位提醒");
    }

    private PortfolioFund trackedPortfolioFund() {
        return PortfolioFund.rehydrate(
                11L, 101L, 3L, 5L, PortfolioFundValidity.TRACKED,
                true, new BigDecimal("0.30"), null, null, null);
    }
}
