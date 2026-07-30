package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionFailure;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PortfolioCorrectionController.class)
class PortfolioCorrectionExceptionHandler {
    @ExceptionHandler(PortfolioCorrectionFailure.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    AccountingApiResponse<Void> handle(PortfolioCorrectionFailure failure) {
        return AccountingApiResponse.error(failure.code().name(), failure.getMessage());
    }
}
