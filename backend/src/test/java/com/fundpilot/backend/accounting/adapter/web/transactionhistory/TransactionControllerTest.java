package com.fundpilot.backend.accounting.adapter.web.transactionhistory;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.query.positiontracking.OpenLotQueryHandler;
import com.fundpilot.backend.accounting.application.query.transactionhistory.TransactionQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TransactionController.class)
@Import({TransactionController.class, TransactionExceptionHandler.class, TransactionControllerTest.TestConfig.class})
class TransactionControllerTest {
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean TransactionLedgerCommandHandler ledgerCommands;
    @MockitoBean TransactionConfirmationCommandHandler confirmationCommands;
    @MockitoBean TransactionQueryHandler queries;
    @MockitoBean OpenLotQueryHandler openLots;

    @Test
    void openLotsPreservesZeroFeeAndMissingReason() throws Exception {
        when(openLots.find(7L, 41L)).thenReturn(new OpenLotQueryHandler.Summary(41L,
                Instant.parse("2026-09-07T00:00:00Z"), new BigDecimal("1.20"), BigDecimal.ZERO, null,
                List.of(new OpenLotQueryHandler.LotEstimate(Instant.parse("2026-09-01T00:00:00Z"),
                        new BigDecimal("80"), 7L, BigDecimal.ZERO, BigDecimal.ZERO, null))));

        mockMvc.perform(get("/api/portfolio-funds/41/open-lots")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedRedemptionFee").value(0))
                .andExpect(jsonPath("$.data.unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.data.lots[0].redemptionRate").value(0))
                .andExpect(jsonPath("$.data.lots[0].holdingDays").value(7));
    }

    @Test
    void legacyTransactionListRouteIsRemoved() throws Exception {
        mockMvc.perform(get("/api/funds/17/transactions")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyTransactionRecordRouteIsRemoved() throws Exception {
        mockMvc.perform(post("/api/funds/17/transactions")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"source\":\"INCREASE\",\"amount\":100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void portfolioTransactionRecordForwardsPortfolioFundTarget() throws Exception {
        Instant tradeDate = Instant.parse("2026-08-20T00:00:00Z");
        var result = new TransactionLedgerCommandHandler.LedgerResult(
                99L, 41L, 7L, "TRANSFER_OUT", "PENDING", null, new java.math.BigDecimal("10"),
                null, null, null, tradeDate, null, null, tradeDate, 42L, null, null, null, null);
        when(ledgerCommands.recordManual(eq(7L), eq(41L),
                eq(TransactionLedgerCommandHandler.Source.TRANSFER_OUT), isNull(),
                eq(new java.math.BigDecimal("10")), eq(tradeDate), eq(42L))).thenReturn(result);
        when(queries.findViewById(7L, 99L)).thenReturn(Optional.of(
                new TransactionQueryHandler.TransactionViewResult(result, null)));

        mockMvc.perform(post("/api/portfolio-funds/41/transactions")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"source\":\"TRANSFER_OUT\",\"shares\":10,"
                                + "\"targetPortfolioFundId\":42,\"tradeDate\":\"2026-08-20T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolioFundId").value(41));

        verify(ledgerCommands).recordManual(eq(7L), eq(41L),
                eq(TransactionLedgerCommandHandler.Source.TRANSFER_OUT), isNull(),
                eq(new java.math.BigDecimal("10")), eq(tradeDate), eq(42L));
    }

    @Test
    void portfolioTransactionListReturnsNotFoundForInvalidPortfolioFund() throws Exception {
        when(queries.findByPortfolioFund(7L, 41L)).thenThrow(new TransactionLedgerFailure(
                TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND, "组合基金不存在: 41"));

        mockMvc.perform(get("/api/portfolio-funds/41/transactions")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_NOT_FOUND"));
    }
}
