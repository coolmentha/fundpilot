package com.fundpilot.backend.accounting.adapter.web.transactionhistory;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationFailure;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TransactionController.class)
class TransactionExceptionHandler {
    @ExceptionHandler(TransactionLedgerFailure.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    TransactionController.Response<Void> handle(TransactionLedgerFailure failure) {
        return TransactionController.Response.error(failure.code().name(), failure.getMessage());
    }

    @ExceptionHandler(TransactionConfirmationFailure.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    TransactionController.Response<Void> handle(TransactionConfirmationFailure failure) {
        return TransactionController.Response.error(failure.code().name(), failure.getMessage());
    }
}
