package com.fundpilot.backend.accounting.adapter.web.transactionconfirmation;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionCompensationCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理 - 交易确认接口", description = "交易确认相关操作")
@RestController
@RequiredArgsConstructor
public class TransactionConfirmationAdminController {
    private final TransactionCompensationCommandHandler compensation;
    private final Clock clock;

    @PostMapping("/api/admin/transactions/confirm-nav")
    @Operation(summary = "批量确认交易净值")
    public ApiResponse<Map<String, Object>> confirmNav() {
        return ApiResponse.ok(Map.of("confirmed", compensation.compensateAll(clock.instant())));
    }
}
