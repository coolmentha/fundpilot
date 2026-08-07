package com.fundpilot.backend.market.client;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsClientConfig;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsEtfSpotClient;
import feign.Feign;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThsEtfSpotClientTest {

    private MockWebServer server;
    private ThsEtfSpotClient client;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        client = Feign.builder()
                .requestInterceptor(ThsClientConfig.requestInterceptor())
                .options(ThsClientConfig.options())
                .target(ThsEtfSpotClient.class, server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchSpotRaw_按AKShare路径请求() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("g({})"));

        client.fetchSpotRaw();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/data/Net/info/ETF_rate_desc_0_0_1_9999_0_0_0_jsonp_g.html");
        assertThat(request.getHeader("Referer")).isEqualTo("https://fund.10jqka.com.cn/");
    }
}
