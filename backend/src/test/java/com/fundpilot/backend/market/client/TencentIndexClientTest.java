package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Feign;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TencentIndexClientTest {

    private MockWebServer server;
    private TencentIndexClient client;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        client = Feign.builder()
                .requestInterceptor(EastmoneyClientConfig.tencentRequestInterceptor())
                .options(EastmoneyClientConfig.options())
                .target(TencentIndexClient.class, server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchKlineRaw_按AKShare腾讯接口构造参数并带请求头() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.fetchKlineRaw("sh000300", "2026-01-01", "2026-08-07");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("/ifzqgtimg/appstock/app/newfqkline/get");
        assertThat(request.getPath()).contains("sh000300");
        assertThat(request.getPath()).contains("2026-01-01");
        assertThat(request.getPath()).contains("640");
        assertThat(request.getHeader("Referer")).isEqualTo("https://gu.qq.com/");
        assertThat(request.getHeader("User-Agent")).isNotBlank();
    }
}
