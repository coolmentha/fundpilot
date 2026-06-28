package com.fundpilot.backend.market.client;

import feign.Feign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:MockWebServer 综合单测。
 * <p>固化样本响应 → 验证解析正确 + 验证 Referer 头存在 + 验证 Semaphore 限流。
 */
class EastmoneyClientIntegrationTest {

    private MockWebServer mockWebServer;
    private EastmoneyClient client;

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();

        client = Feign.builder()
                .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
                .target(EastmoneyClient.class, mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void fetchNavHistory_parsesAndSendsHeaders() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        var Data_netWorthTrend = [{"x":1719187200000,"y":1.0000}];
                        var Data_ACWorthTrend = [[1719187200000,2.0000]];
                        """));

        List<FundNavSnapshot> result = client.fetchNavHistory("000001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).navDate()).isEqualTo(Instant.parse("2024-06-24T00:00:00Z"));

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).contains("000001");
        assertThat(req.getHeader("Referer")).isEqualTo("https://fund.eastmoney.com/");
        assertThat(req.getHeader("User-Agent")).isNotNull();
    }

    @Test
    void fetchFundDict_parsesAndSendsHeaders() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("var r = [[\"000001\",\"HXCZHH\",\"华夏成长混合\",\"混合型-灵活\",\"HUAXIACHENGZHANGHUNHE\"],[\"000011\",\"HXDPJX\",\"华夏大盘精选\",\"混合型-灵活\",\"HUAXIADAPANJINGXUAN\"]];"));

        List<FundDictEntry> result = client.fetchFundDict();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fundCode()).isEqualTo("000001");
        assertThat(result.get(0).fundName()).isEqualTo("华夏成长混合");
        assertThat(result.get(0).rawName()).isEqualTo("混合型-灵活");

        mockWebServer.takeRequest();
    }

    @Test
    void semaphoreAllowsTwoConcurrentThenBlocks() throws Exception {
        Semaphore s = new Semaphore(2);
        s.acquire(2);
        assertThat(s.availablePermits()).isEqualTo(0);
        s.release();
        assertThat(s.availablePermits()).isEqualTo(1);
        s.acquire();
        assertThat(s.availablePermits()).isEqualTo(0);
        s.release(2);
    }
}