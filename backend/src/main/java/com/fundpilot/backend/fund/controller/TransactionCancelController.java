package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.TransactionCancelService;
import com.fundpilot.backend.fund.service.TransactionConfirmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易撤单 + 手动确认 Controller(issue #15):撤单 PENDING→CANCELLED,确认 PENDING→CONFIRMED。
 * 返回 {@link FundTransactionView} DTO,不直接暴露 Entity。
 */
@RestController
@RequiredArgsConstructor
public class TransactionCancelController {

    private final TransactionCancelService transactionCancelService;
    private final TransactionConfirmService transactionConfirmService;

    @PostMapping("/api/transactions/{id}/cancel")
    public ApiResponse<List<FundTransactionView>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(transactionCancelService.cancel(id).stream()
                .map(FundTransactionView::from).toList());
    }

    /** 手动确认交易:取最新净值回填另一侧,转换交易两条腿联动确认。 */
    @PostMapping("/api/transactions/{id}/confirm")
    public ApiResponse<List<FundTransactionView>> confirm(@PathVariable Long id) {
        return ApiResponse.ok(transactionConfirmService.confirm(id).stream()
                .map(FundTransactionView::from).toList());
    }
}
