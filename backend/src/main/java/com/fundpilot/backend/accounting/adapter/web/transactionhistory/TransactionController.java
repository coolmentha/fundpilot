package com.fundpilot.backend.accounting.adapter.web.transactionhistory;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.query.transactionhistory.TransactionQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionLedgerCommandHandler ledgerCommands;
    private final TransactionConfirmationCommandHandler confirmationCommands;
    private final TransactionQueryHandler queries;

    @GetMapping("/api/funds/{legacyFundId}/transactions")
    public Response<List<TransactionView>> list(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long legacyFundId) {
        return Response.ok(queries.findByLegacyFund(ownerId, legacyFundId).stream()
                .map(TransactionView::from).toList());
    }

    @PostMapping("/api/funds/{legacyFundId}/transactions")
    public Response<TransactionView> record(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long legacyFundId,
            @RequestBody ManualTransactionRequest request) {
        var result = ledgerCommands.recordManualForLegacyFund(ownerId, legacyFundId, request.source(),
                request.amount(), request.shares(), request.tradeDate(), request.targetFundId());
        return Response.ok(view(ownerId, result.transactionId()));
    }

    @GetMapping("/api/portfolio-funds/{portfolioFundId}/transactions")
    public Response<List<TransactionView>> listPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId) {
        return Response.ok(queries.findByPortfolioFund(ownerId, portfolioFundId).stream()
                .map(result -> TransactionView.from(result, null, null, null, null, null)).toList());
    }

    @PostMapping("/api/portfolio-funds/{portfolioFundId}/transactions")
    public Response<TransactionView> recordPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId,
            @RequestBody ManualTransactionRequest request) {
        var result = ledgerCommands.recordManual(ownerId, portfolioFundId, request.source(), request.amount(),
                request.shares(), request.tradeDate(), request.targetPortfolioFundId());
        return Response.ok(view(ownerId, result.transactionId()));
    }

    @GetMapping("/api/transactions/pending")
    public Response<List<TransactionView>> pending(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return Response.ok(queries.findPendingByOwner(ownerId).stream()
                .map(TransactionView::from).toList());
    }

    @PutMapping("/api/transactions/{transactionId}")
    public Response<TransactionView> revise(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long transactionId,
            @RequestBody PendingTransactionUpdateRequest request) {
        ledgerCommands.revisePending(ownerId, transactionId, request.amount(), request.shares(), request.tradeDate());
        return Response.ok(view(ownerId, transactionId));
    }

    @PostMapping("/api/transactions/{transactionId}/cancel")
    public Response<List<TransactionView>> cancel(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long transactionId) {
        return Response.ok(confirmationCommands.cancel(ownerId, transactionId).stream()
                .map(id -> view(ownerId, id)).toList());
    }

    @PostMapping("/api/transactions/{transactionId}/confirm")
    public Response<List<TransactionView>> confirm(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long transactionId) {
        return Response.ok(confirmationCommands.confirm(ownerId, transactionId).stream()
                .map(id -> view(ownerId, id)).toList());
    }

    private TransactionView view(long ownerId, long transactionId) {
        return queries.findViewById(ownerId, transactionId).map(TransactionView::from)
                .orElseThrow(() -> new TransactionLedgerFailure(
                        TransactionLedgerFailure.Code.TRANSACTION_NOT_FOUND,
                        "交易不存在: " + transactionId));
    }

    public record ManualTransactionRequest(TransactionLedgerCommandHandler.Source source, BigDecimal amount,
                                           BigDecimal shares, Long targetFundId, Long targetPortfolioFundId,
                                           Instant tradeDate) {
    }

    public record PendingTransactionUpdateRequest(BigDecimal amount, BigDecimal shares, Instant tradeDate) {
    }

    public record TransactionView(long id, Long fundId, long portfolioFundId, BigDecimal amount, BigDecimal shares, BigDecimal nav,
                                  BigDecimal fee, BigDecimal feeRate, String status, String source,
                                  Instant confirmTime, Instant cancelTime, Long signalLogId,
                                  Long relatedTransactionId, Instant tradeDate, Instant createdDate,
                                  BigDecimal expectedNav, BigDecimal expectedShares,
                                  String confirmationState, String confirmationReason,
                                  boolean qdii, String signalReason) {
        static TransactionView from(TransactionQueryHandler.TransactionViewResult result) {
            return from(result.transaction(), result.legacyFundId(), null, null, null, null);
        }

        static TransactionView from(TransactionQueryHandler.PendingResult result) {
            return from(result.transaction(), result.legacyFundId(), result.expectedNav(), result.expectedShares(),
                    result.confirmationState(), result.confirmationReason());
        }

        private static TransactionView from(TransactionLedgerCommandHandler.LedgerResult result, Long legacyFundId,
                                            BigDecimal expectedNav, BigDecimal expectedShares,
                                            String confirmationState, String confirmationReason) {
            return new TransactionView(result.transactionId(), legacyFundId, result.portfolioFundId(), result.amount(), result.shares(),
                    result.nav(), result.fee(), result.feeRate(), result.status(), result.source(),
                    result.confirmTime(), result.cancelTime(), result.signalLogId(), result.relatedTransactionId(),
                    result.tradeDate(), result.createdDate(), expectedNav, expectedShares, confirmationState,
                    confirmationReason, false, null);
        }
    }

    public record Response<T>(boolean success, T data, String code, String message) {
        static <T> Response<T> ok(T data) {
            return new Response<>(true, data, null, null);
        }

        static Response<Void> error(String code, String message) {
            return new Response<>(false, null, code, message);
        }
    }
}
