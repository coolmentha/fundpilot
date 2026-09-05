package com.fundpilot.backend.portfolio.adapter.web.fundtracking;

import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingFailure;
import com.fundpilot.backend.portfolio.application.command.fundtracking.PortfolioFundFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PortfolioFundConfigurationController.class)
class PortfolioFundConfigurationExceptionHandler {
    @ExceptionHandler(PortfolioFundFailure.class)
    ResponseEntity<ApiResponse<Void>> handlePortfolioFund(PortfolioFundFailure failure) {
        return ResponseEntity.status(statusOf(failure.code()))
                .body(ApiResponse.error(failure.code().name(), failure.getMessage()));
    }

    @ExceptionHandler(FundGroupingFailure.class)
    ResponseEntity<ApiResponse<Void>> handleGrouping(FundGroupingFailure failure) {
        return ResponseEntity.status(statusOf(failure.code()))
                .body(ApiResponse.error(failure.code().name(), failure.getMessage()));
    }

    private HttpStatus statusOf(PortfolioFundFailure.Code code) {
        return switch (code) {
            case PORTFOLIO_FUND_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PORTFOLIO_FUND_VOIDED -> HttpStatus.CONFLICT;
            case POSITION_WARNING_INVALID, PRODUCT_NOT_FOUND, PORTFOLIO_FUND_ALREADY_TRACKED,
                    VOID_REASON_REQUIRED -> HttpStatus.BAD_REQUEST;
        };
    }

    private HttpStatus statusOf(FundGroupingFailure.Code code) {
        return switch (code) {
            case PORTFOLIO_FUND_NOT_FOUND, FUND_GROUP_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FUND_GROUP_NAME_INVALID, FUND_GROUP_NAME_DUPLICATE -> HttpStatus.BAD_REQUEST;
        };
    }
}
