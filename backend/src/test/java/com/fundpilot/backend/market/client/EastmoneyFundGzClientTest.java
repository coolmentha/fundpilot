package com.fundpilot.backend.market.client;

import feign.Feign;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #36 验收:fundgz 盘中估值 client MockWebServer 测试。
 * <p>验证:① 请求带 Referer/UA 头 ② jsonpgz 响应被正确解析为 FundEstimateSnapshot。
 */
class EastmoneyFundGzClientTest {

    private MockWebServer mockWebServer;
    private EastmoneyFundGzClient client;

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        client = Feign.builder()
                .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
                .target(EastmoneyFundGzClient.class, mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void fetchGzRaw_parsesJsonpgzAndSendsHeaders() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("jsonpgz({\"fundcode\":\"008585\",\"jzrq\":\"2026-06-25\","
                        + "\"dwjz\":\"1.9700\",\"gsz\":\"1.8790\",\"gszzl\":\"-4.62\","
                        + "\"gztime\":\"2026-06-26 15:00\"});"));

        String raw = client.fetchGzRaw("008585");
        FundEstimateSnapshot snapshot = EastmoneyJsParser.parseFundGz(raw);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.estimatedChangePct()).isEqualByComparingTo(new BigDecimal("-0.0462"));
        assertThat(snapshot.estimateTime()).isEqualTo("2026-06-26 15:00");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).contains("008585");
        assertThat(req.getHeader("Referer")).isEqualTo("https://fund.eastmoney.com/");
        assertThat(req.getHeader("User-Agent")).isNotNull();
    }
}
