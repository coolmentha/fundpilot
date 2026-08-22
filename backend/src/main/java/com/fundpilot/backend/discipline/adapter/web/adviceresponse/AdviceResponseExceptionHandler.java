package com.fundpilot.backend.discipline.adapter.web.adviceresponse;

import com.fundpilot.backend.platform.web.ApiResponse;

import com.fundpilot.backend.discipline.application.command.adviceresponse.AdviceResponseFailure;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdviceResponseController.class)
class AdviceResponseExceptionHandler {
    @ExceptionHandler(AdviceResponseFailure.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> handle(AdviceResponseFailure failure) {
        return ApiResponse.error(failure.code().name(), failure.getMessage());
    }
}


