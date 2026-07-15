package com.fundpilot.backend.market.client;

import feign.Feign;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThsMarketDataSourceIntegrationTest {

    private final MockWebServer server = new MockWebServer();

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchNavHistory_真实发出单位和累计净值两次请求() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("var dwjz_510300=[[\"20260715\",\"4.8336\"]];"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("var ljjz_510300=[[\"20260715\",\"2.1195\"]];"));
        ThsClient client = Feign.builder()
                .requestInterceptor(ThsClientConfig.requestInterceptor())
                .options(ThsClientConfig.options())
                .target(ThsClient.class, server.url("/").toString());
        ThsMarketDataSource source = new ThsMarketDataSource(client, () -> "g({})", code -> "callback({})");

        var result = source.fetchNavHistory("510300");

        assertThat(result).hasSize(1);
        assertThat(server.takeRequest().getPath()).isEqualTo("/510300/json/jsondwjz.json");
        assertThat(server.takeRequest().getPath()).isEqualTo("/510300/json/jsonljjz.json");
    }
}
