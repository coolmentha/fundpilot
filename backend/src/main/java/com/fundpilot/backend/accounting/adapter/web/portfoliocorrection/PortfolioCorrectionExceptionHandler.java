package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

import com.fundpilot.backend.platform.web.ApiResponse;

import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PortfolioCorrectionController.class)
class PortfolioCorrectionExceptionHandler {
    @ExceptionHandler(PortfolioCorrectionFailure.class)
    ResponseEntity<ApiResponse<Void>> handle(PortfolioCorrectionFailure failure) {
        return ResponseEntity.status(statusOf(failure.code()))
                .body(ApiResponse.error(failure.code().name(), failure.getMessage()));
    }

    private HttpStatus statusOf(PortfolioCorrectionFailure.Code code) {
        return switch (code) {
            case PORTFOLIO_FUND_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PORTFOLIO_FUND_NOT_OPEN, PORTFOLIO_FUND_CORRECTION_CONFLICT -> HttpStatus.CONFLICT;
            case COST_PER_SHARE_INVALID, VOID_CONFIRMATION_REQUIRED, VOID_REASON_REQUIRED,
                    PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS -> HttpStatus.BAD_REQUEST;
        };
    }
}


