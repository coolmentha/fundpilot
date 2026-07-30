package com.fundpilot.backend.importing.adapter.web.importsession;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = YangjibaoImportController.class)
class YangjibaoImportExceptionHandler {
    @ExceptionHandler(YangjibaoImportFailure.class)
    ResponseEntity<ImportingApiResponse<Void>> handle(YangjibaoImportFailure failure) {
        return ResponseEntity.badRequest().body(ImportingApiResponse.error(failure.code().name(), failure.getMessage()));
    }
}
