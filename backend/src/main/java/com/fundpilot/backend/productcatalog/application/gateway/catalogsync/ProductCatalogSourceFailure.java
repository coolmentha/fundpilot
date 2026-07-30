package com.fundpilot.backend.productcatalog.application.gateway.catalogsync;

public final class ProductCatalogSourceFailure extends RuntimeException {
    private final Kind kind;

    public ProductCatalogSourceFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ProductCatalogSourceFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    public enum Kind { UNAVAILABLE, INVALID_RESPONSE }
}
