package com.fundpilot.backend.productcatalog.infrastructure.remote.catalogsync;

import feign.RequestLine;

public interface EastmoneyProductCatalogClient {
    @RequestLine("GET /js/fundcode_search.js")
    String fetchRaw();
}
