package com.fundpilot.backend.market.client;

import feign.Feign;
import feign.RequestLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:EastmoneyClient Feign 接口发出的每个请求带 Referer / User-Agent 头。
 * <p>用 MockWebServer 抓取请求头,不依赖真实东方财富服务。
 * Feign 客户端通过 {@link Feign#builder()} 编程构造,注入生产级 {@link EastmoneyClientConfig} 的拦截器。
 */
class EastmoneyClientHeadersTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void sendsRefererAndUserAgentHeaders() throws Exception {
        mockWebServer.enqueue(new okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setBody("{}"));

        EastmoneyClient client = Feign.builder()
                .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
                .target(EastmoneyClient.class, mockWebServer.url("/").toString());

        client.fetchRaw("/test");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Referer")).isEqualTo("https://fund.eastmoney.com/");
        assertThat(request.getHeader("User-Agent")).isNotNull();
    }
}