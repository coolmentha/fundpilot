package com.fundpilot.backend.marketdata.adapter.web.watchedindex;

record MarketDataApiResponse<T>(boolean success, T data) {

    static <T> MarketDataApiResponse<T> ok(T data) {
        return new MarketDataApiResponse<>(true, data);
    }
}
