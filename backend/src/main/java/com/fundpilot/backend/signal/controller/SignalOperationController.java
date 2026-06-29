package com.fundpilot.backend.signal.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.signal.service.SignalOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基金操作确认 Controller(issue #14):用户回应信号的 HTTP 入口。
 * <p>{@code POST /api/funds/{fundId}/operations} —— fundId 用于路径,body 含 signalLogId,
 * service 内部校验 signalLog 归属该 fund(通过 SignalLog.fundEntity.id)。
 * 返回 {@link FundTransactionView} DTO,不直接暴露 Entity。
 */
@RestController
@RequiredArgsConstructor
public class SignalOperationController {

    private final SignalOperationService signalOperationService;

    @PostMapping("/api/funds/{fundId}/operations")
    public ApiResponse<FundTransactionView> confirmOperation(@PathVariable Long fundId,
                                                              @RequestBody ConfirmOperationRequest request) {
        return ApiResponse.ok(FundTransactionView.from(
                signalOperationService.confirmOperation(request.signalLogId(), request)));
    }
}
