package com.fundpilot.backend.accounting.adapter.web.openapi;

import com.fundpilot.backend.accounting.application.query.transactionhistory.TransactionQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Open API v1 交易流水只读资源。 */
@Tag(name = "Open API 交易", description = "当前用户的交易流水与待确认记录")
@RestController
@RequestMapping("/api/open/v1")
@RequiredArgsConstructor
public class OpenApiTransactionController {

    private final TransactionQueryHandler queries;

    @GetMapping("/portfolio-funds/{portfolioFundId}/transactions")
    @Operation(summary = "查询组合基金交易流水")
    public ApiResponse<List<TransactionView>> transactions(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId) {
        return ApiResponse.ok(queries.findByPortfolioFund(ownerId, portfolioFundId).stream()
                .map(OpenApiTransactionController::transactionView).toList());
    }

    @GetMapping("/transactions/pending")
    @Operation(summary = "查询待确认交易")
    public ApiResponse<List<TransactionView>> pending(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.findPendingByOwner(ownerId).stream()
                .map(OpenApiTransactionController::pendingView).toList());
    }

    private static TransactionView transactionView(
            com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerCommandHandler.LedgerResult value) {
        return new TransactionView(value.transactionId(), value.portfolioFundId(), value.amount(), value.shares(),
                value.nav(), value.fee(), value.feeRate(), value.status(), value.source(), value.confirmTime(),
                value.cancelTime(), value.tradeDate(), value.createdDate());
    }

    private static TransactionView pendingView(TransactionQueryHandler.PendingResult value) {
        return transactionView(value.transaction());
    }

    public record TransactionView(long id, long portfolioFundId, BigDecimal amount, BigDecimal shares, BigDecimal nav,
                                  BigDecimal fee, BigDecimal feeRate, String status, String source,
                                  Instant confirmTime, Instant cancelTime, Instant tradeDate, Instant createdDate) {
    }
}
