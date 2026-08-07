package com.fundpilot.backend.market.client;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyClientConfig;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyEtfSpotClient;
import feign.Feign;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EastmoneyEtfSpotClientTest {

    private MockWebServer server;
    private EastmoneyEtfSpotClient client;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        client = Feign.builder()
                .requestInterceptor(EastmoneyClientConfig.etfRequestInterceptor())
                .options(EastmoneyClientConfig.options())
                .target(EastmoneyEtfSpotClient.class, server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchSpotPageRaw_按AKShare分页和IOPV字段请求() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.fetchSpotPageRaw(2);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("/api/qt/clist/get");
        assertThat(request.getPath()).contains("pn=2");
        assertThat(request.getPath()).contains("f441");
        assertThat(request.getPath()).contains("MK0021");
        assertThat(request.getHeader("Referer"))
                .isEqualTo("https://quote.eastmoney.com/center/gridlist.html#fund_etf");
    }
}
