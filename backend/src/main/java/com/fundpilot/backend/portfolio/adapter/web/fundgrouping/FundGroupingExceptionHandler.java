package com.fundpilot.backend.portfolio.adapter.web.fundgrouping;

import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingFailure;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FundGroupingController.class)
class FundGroupingExceptionHandler {
    @ExceptionHandler(FundGroupingFailure.class)
    FundGroupingController.Response<Void> handle(FundGroupingFailure failure) {
        return new FundGroupingController.Response<>(false, null, failure.code().name(),
                failure.getMessage());
    }
}
