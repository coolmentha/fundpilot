package com.fundpilot.backend.accounting.adapter.web.transactionhistory;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import com.fundpilot.backend.accounting.application.query.transactionhistory.TransactionQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "交易流水接口", description = "交易流水相关操作")
@RestController
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionLedgerCommandHandler ledgerCommands;
    private final TransactionConfirmationCommandHandler confirmationCommands;
    private final TransactionQueryHandler queries;

    @GetMapping("/api/funds/{legacyFundId}/transactions")
    @Operation(summary = "查询基金交易流水")
    public Response<List<TransactionView>> list(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long legacyFundId) {
        return Response.ok(queries.findByLegacyFund(ownerId, legacyFundId).stream()
                .map(TransactionView::from).toList());
    }

    @PostMapping("/api/funds/{legacyFundId}/transactions")
    @Operation(summary = "登记基金手动交易")
    public Response<TransactionView> record(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long legacyFundId,
            @RequestBody ManualTransactionRequest request) {
        var result = ledgerCommands.recordManualForLegacyFund(ownerId, legacyFundId, request.source(),
                request.amount(), request.shares(), request.tradeDate(), request.targetFundId());
        return Response.ok(view(ownerId, result.transactionId()));
    }

    @GetMapping("/api/portfolio-funds/{portfolioFundId}/transactions")
    @Operation(summary = "查询组合基金交易流水")
    public Response<List<TransactionView>> listPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId) {
        return Response.ok(queries.findByPortfolioFund(ownerId, portfolioFundId).stream()
                .map(result -> TransactionView.from(result, null, null, null, null, null, null)).toList());
    }

    @PostMapping("/api/portfolio-funds/{portfolioFundId}/transactions")
    @Operation(summary = "登记组合基金手动交易")
    public Response<TransactionView> recordPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId,
            @RequestBody ManualTransactionRequest request) {
        var result = ledgerCommands.recordManual(ownerId, portfolioFundId, request.source(), request.amount(),
                request.shares(), request.tradeDate(), request.targetPortfolioFundId());
        return Response.ok(view(ownerId, result.transactionId()));
    }

    @GetMapping("/api/transactions/pending")
    @Operation(summary = "查询待确认交易列表")
    public Response<List<TransactionView>> pending(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return Response.ok(queries.findPendingByOwner(ownerId).stream()
                .map(TransactionView::from).toList());
    }

    @PutMapping("/api/transactions/{transactionId}")
    @Operation(summary = "修订待确认交易")
    public Response<TransactionView> revise(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long transactionId,
            @RequestBody PendingTransactionUpdateRequest request) {
        ledgerCommands.revisePending(ownerId, transactionId, request.amount(), request.shares(), request.tradeDate());
        return Response.ok(view(ownerId, transactionId));
    }

    @PostMapping("/api/transactions/{transactionId}/cancel")
    @Operation(summary = "取消交易")
    public Response<List<TransactionView>> cancel(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long transactionId) {
        return Response.ok(confirmationCommands.cancel(ownerId, transactionId).stream()
                .map(id -> view(ownerId, id)).toList());
    }

    @PostMapping("/api/transactions/{transactionId}/confirm")
    @Operation(summary = "确认交易")
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

    @Schema(description = "手动交易登记请求")
    public record ManualTransactionRequest(
            @Schema(description = "交易来源，枚举（INCREASE 增加 / DECREASE 减少 / TRANSFER_IN 转入 / TRANSFER_OUT 转出 / INVEST 定投 / ADJUST_IN 调整转入 / ADJUST_OUT 调整转出 / COST_BASIS_RESET 成本基准重置）", example = "INVEST") TransactionLedgerCommandHandler.Source source,
            @Schema(description = "交易金额", example = "1000.00") BigDecimal amount,
            @Schema(description = "交易份额", example = "500.00") BigDecimal shares,
            @Schema(description = "目标基金ID（legacy），按基金记账时填写", example = "3001") Long targetFundId,
            @Schema(description = "目标组合基金ID，按组合基金记账时填写", example = "4001") Long targetPortfolioFundId,
            @Schema(description = "交易日期", example = "2026-08-20T08:00:00Z") Instant tradeDate) {
    }

    @Schema(description = "待确认交易修订请求")
    public record PendingTransactionUpdateRequest(@Schema(description = "修订后金额", example = "1200.00") BigDecimal amount,
                                                  @Schema(description = "修订后份额", example = "600.00") BigDecimal shares,
                                                  @Schema(description = "修订后交易日期", example = "2026-08-20T08:00:00Z") Instant tradeDate) {
    }

    @Schema(description = "交易流水视图")
    public record TransactionView(long id, Long fundId, long portfolioFundId, BigDecimal amount, BigDecimal shares, BigDecimal nav,
                                  BigDecimal fee, BigDecimal feeRate, String status, String source,
                                  Instant confirmTime, Instant cancelTime, Long signalLogId,
                                  Long relatedTransactionId, Instant tradeDate, Instant createdDate,
                                  BigDecimal expectedNav, BigDecimal expectedShares,
                                  String confirmationState, String confirmationReason,
                                  boolean qdii, String signalReason) {
        static TransactionView from(TransactionQueryHandler.TransactionViewResult result) {
            return from(result.transaction(), result.legacyFundId(), null, null, null, null, null);
        }

        static TransactionView from(TransactionQueryHandler.PendingResult result) {
            return from(result.transaction(), result.legacyFundId(), result.expectedNav(), result.expectedShares(),
                    result.confirmationState(), result.confirmationReason(), result.signalReason());
        }

        private static TransactionView from(TransactionLedgerCommandHandler.LedgerResult result, Long legacyFundId,
                                            BigDecimal expectedNav, BigDecimal expectedShares,
                                            String confirmationState, String confirmationReason,
                                            String signalReason) {
            return new TransactionView(result.transactionId(), legacyFundId, result.portfolioFundId(), result.amount(), result.shares(),
                    result.nav(), result.fee(), result.feeRate(), result.status(), result.source(),
                    result.confirmTime(), result.cancelTime(), result.signalLogId(), result.relatedTransactionId(),
                    result.tradeDate(), result.createdDate(), expectedNav, expectedShares, confirmationState,
                    confirmationReason, false, signalReason);
        }
    }

    @Schema(description = "统一响应结果")
    public record Response<T>(boolean success, T data, String code, String message) {
        static <T> Response<T> ok(T data) {
            return new Response<>(true, data, null, null);
        }

        static Response<Void> error(String code, String message) {
            return new Response<>(false, null, code, message);
        }
    }
}
