package com.fundpilot.backend.portfolio.adapter.web.fundtracking;

import com.fundpilot.backend.platform.web.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = PortfolioFundController.class)
class PortfolioFundQueryExceptionHandler {
    @ExceptionHandler(PortfolioFundController.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiResponse<Void> handle(PortfolioFundController.NotFound failure) {
        return ApiResponse.error(failure.code().name(), failure.getMessage());
    }
}
