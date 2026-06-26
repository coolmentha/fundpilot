package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.TransactionCancelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易撤单 Controller(issue #15):PENDING → CANCELLED;转换交易两条腿一起撤。
 * 返回 {@link FundTransactionView} DTO,不直接暴露 Entity。
 */
@RestController
@RequiredArgsConstructor
public class TransactionCancelController {

    private final TransactionCancelService transactionCancelService;

    @PostMapping("/api/transactions/{id}/cancel")
    public ApiResponse<List<FundTransactionView>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(transactionCancelService.cancel(id).stream()
                .map(FundTransactionView::from).toList());
    }
}
