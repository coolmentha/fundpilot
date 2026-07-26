package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.controller.ManualTransactionRequest;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.fund.service.FundTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基金 Controller(issue #16):基金 CRUD,只做 HTTP 路由,业务逻辑下沉 {@link FundService}。
 * 返回 {@link FundView} DTO,不直接暴露 Entity。
 */
@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;
    private final FundTransactionService fundTransactionService;

    @GetMapping
    public ApiResponse<List<FundView>> list() {
        return ApiResponse.ok(fundService.list());
    }

    @PostMapping
    public ApiResponse<FundView> create(@RequestBody FundCreateRequest request) {
        return ApiResponse.ok(fundService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<FundView> get(@PathVariable Long id) {
        return ApiResponse.ok(fundService.get(id));
    }

    /** 查某基金交易流水,按交易发生时间倒序(issue #18 交易流水 Tab)。 */
    @GetMapping("/{id}/transactions")
    public ApiResponse<List<FundTransactionView>> transactions(@PathVariable Long id) {
        return ApiResponse.ok(fundTransactionService.listByFund(id));
    }

    /** 手动录入一笔交易(issue #18 手动交易,绕过信号)。 */
    @PostMapping("/{id}/transactions")
    public ApiResponse<FundTransactionView> createManualTransaction(
            @PathVariable Long id, @RequestBody ManualTransactionRequest request) {
        return ApiResponse.ok(fundTransactionService.createManual(id, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FundView> update(@PathVariable Long id, @RequestBody FundCreateRequest request) {
        return ApiResponse.ok(fundService.update(id, request));
    }

}
