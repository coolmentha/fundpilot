package com.fundpilot.backend.accounting.adapter.web.transactionconfirmation;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionCompensationCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransactionConfirmationAdminController {
    private final TransactionCompensationCommandHandler compensation;
    private final Clock clock;

    @PostMapping("/api/admin/transactions/confirm-nav")
    public ApiResponse<Map<String, Object>> confirmNav() {
        return ApiResponse.ok(Map.of("confirmed", compensation.compensateAll(clock.instant())));
    }
}
