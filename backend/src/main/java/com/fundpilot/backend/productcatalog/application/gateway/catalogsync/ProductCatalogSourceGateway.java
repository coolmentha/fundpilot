package com.fundpilot.backend.productcatalog.application.gateway.catalogsync;

import java.util.List;

public interface ProductCatalogSourceGateway {
    List<SourceProduct> fetchAll();

    record SourceProduct(String fundCode, String fundName, String rawName) {}
}
