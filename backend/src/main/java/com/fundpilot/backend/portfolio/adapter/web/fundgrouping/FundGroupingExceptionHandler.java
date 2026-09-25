package com.fundpilot.backend.portfolio.adapter.web.fundgrouping;

import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingFailure;
import com.fundpilot.backend.platform.web.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FundGroupingController.class)
class FundGroupingExceptionHandler {
    @ExceptionHandler(FundGroupingFailure.class)
    ApiResponse<Void> handle(FundGroupingFailure failure) {
        return ApiResponse.error(failure.code().name(), failure.getMessage());
    }
}
