package com.fundpilot.backend.productcatalog.adapter.web;

import com.fundpilot.backend.productcatalog.application.command.catalogsync.ProductCatalogFailure;
import com.fundpilot.backend.productcatalog.application.command.feerefresh.FundFeeFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.fundpilot.backend.productcatalog.adapter.web")
class ProductCatalogExceptionHandler {
    @ExceptionHandler(FundFeeFailure.class)
    ResponseEntity<ErrorResponse> fundFeeFailure(FundFeeFailure failure) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(false, failure.code().name(), failure.getMessage()));
    }

    @ExceptionHandler(ProductCatalogFailure.class)
    ResponseEntity<ErrorResponse> productCatalogFailure(ProductCatalogFailure failure) {
        int status = failure.code() == ProductCatalogFailure.Code.PRODUCT_INPUT_INVALID ? 400 : 502;
        return ResponseEntity.status(status)
                .body(new ErrorResponse(false, failure.code().name(), failure.getMessage()));
    }

    record ErrorResponse(boolean success, String code, String message) {}
}
