package com.fundpilot.backend.accounting.adapter.web.fundonboarding;

import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingFailure;
import com.fundpilot.backend.platform.web.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PortfolioFundOnboardingController.class)
class PortfolioFundOnboardingExceptionHandler {
    @ExceptionHandler(PortfolioFundOnboardingFailure.class)
    ResponseEntity<ApiResponse<Void>> handle(PortfolioFundOnboardingFailure failure) {
        return ResponseEntity.status(statusOf(failure.code()))
                .body(ApiResponse.error(failure.code().name(), failure.getMessage()));
    }

    private HttpStatus statusOf(PortfolioFundOnboardingFailure.Code code) {
        return switch (code) {
            case PRODUCT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PORTFOLIO_FUND_ALREADY_TRACKED -> HttpStatus.CONFLICT;
            case NAV_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FUND_GROUP_NOT_FOUND, PORTFOLIO_FUND_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case POSITION_WARNING_INVALID, INITIAL_HOLDING_SHARES_INVALID,
                    COST_PER_SHARE_INVALID, OPENED_AT_IN_FUTURE, FUND_GROUP_NAME_INVALID,
                    FUND_GROUP_NAME_DUPLICATE -> HttpStatus.BAD_REQUEST;
        };
    }
}
