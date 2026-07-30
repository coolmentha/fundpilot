package com.fundpilot.backend.productcatalog.application.command.catalogsync;

public final class ProductCatalogFailure extends RuntimeException {
    private final Code code;

    public ProductCatalogFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ProductCatalogFailure(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }

    public enum Code {
        PRODUCT_INPUT_INVALID,
        PRODUCT_CATALOG_SOURCE_UNAVAILABLE,
        PRODUCT_CATALOG_RESPONSE_INVALID
    }
}
