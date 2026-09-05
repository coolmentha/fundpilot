package com.fundpilot.backend.accounting.adapter.web.transactionhistory;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionConfirmationFailure;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TransactionController.class)
class TransactionExceptionHandler {
    @ExceptionHandler(TransactionLedgerFailure.class)
    ResponseEntity<TransactionController.Response<Void>> handle(TransactionLedgerFailure failure) {
        return ResponseEntity.status(statusOf(failure.code()))
                .body(TransactionController.Response.error(failure.code().name(), failure.getMessage()));
    }

    @ExceptionHandler(TransactionConfirmationFailure.class)
    ResponseEntity<TransactionController.Response<Void>> handle(TransactionConfirmationFailure failure) {
        return ResponseEntity.status(statusOf(failure.code())).body(
                TransactionController.Response.error(failure.code().name(), failure.getMessage()));
    }

    private HttpStatus statusOf(TransactionLedgerFailure.Code code) {
        return code == TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    }

    private HttpStatus statusOf(TransactionConfirmationFailure.Code code) {
        return code == TransactionConfirmationFailure.Code.TRANSACTION_NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    }
}
