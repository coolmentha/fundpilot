package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Feign;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EastmoneyFundEstimatePageClientTest {

    private MockWebServer server;
    private EastmoneyFundEstimatePageClient client;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        client = Feign.builder()
                .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
                .options(EastmoneyClientConfig.options())
                .target(EastmoneyFundEstimatePageClient.class, server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchPageRaw_按AKShare静态页路径请求并携带请求头() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("<html></html>"));

        assertThat(client.fetchPageRaw(3)).isEqualTo("<html></html>");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/fundguzhi3.html");
        assertThat(request.getHeader("Referer")).isEqualTo("https://fund.eastmoney.com/");
        assertThat(request.getHeader("User-Agent")).isNotBlank();
    }
}
