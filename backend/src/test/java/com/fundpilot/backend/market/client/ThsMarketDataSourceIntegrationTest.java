package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

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

    @Test
    void fetchEstimate_请求同花顺分钟估值接口() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("vm_fd_016664='2026-07-17;0930-1500|2026-07-20~2.9763~1500,3.1010,2.9763,0';"));
        ThsFundEstimateClient client = Feign.builder()
                .requestInterceptor(ThsClientConfig.requestInterceptor())
                .options(ThsClientConfig.options())
                .target(ThsFundEstimateClient.class, server.url("/").toString());

        assertThat(ThsJsParser.parseFundEstimate(client.fetchEstimateRaw("016664"))).isNotNull();
        assertThat(server.takeRequest().getPath()).isEqualTo(
                "/?module=api&controller=index&action=chart&info=vm_fd_016664&start=0930");
    }
}
