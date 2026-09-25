package com.fundpilot.backend.importing.adapter.web.importsession;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportFailure;
import com.fundpilot.backend.platform.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = YangjibaoImportController.class)
class YangjibaoImportExceptionHandler {
    @ExceptionHandler(YangjibaoImportFailure.class)
    ResponseEntity<ApiResponse<Void>> handle(YangjibaoImportFailure failure) {
        return ResponseEntity.badRequest().body(ApiResponse.error(failure.code().name(), failure.getMessage()));
    }
}
