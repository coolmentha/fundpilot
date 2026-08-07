package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Feign;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 腾讯证券指数接口真实只读冒烟，默认构建不执行。 */
@Tag("live")
class TencentIndexClientLiveSmokeTest {

    private final TencentIndexClient client = Feign.builder()
            .requestInterceptor(EastmoneyClientConfig.tencentRequestInterceptor())
            .retryer(EastmoneyClientConfig.retryer())
            .options(EastmoneyClientConfig.options())
            .target(TencentIndexClient.class, "https://proxy.finance.qq.com");

    @Test
    void fetchKline_sh000300_returnsBars() {
        IndexKline kline = TencentJsParser.parseIndexKline(
                client.fetchKlineRaw("sh000300", "2026-01-01", "2026-08-08"), "sh000300");

        assertThat(kline.bars()).isNotEmpty();
        assertThat(kline.bars().getLast().close()).isPositive();
    }

    @Test
    void fetchKline_sz399001_returnsBars() {
        IndexKline kline = TencentJsParser.parseIndexKline(
                client.fetchKlineRaw("sz399001", "2026-01-01", "2026-08-08"), "sz399001");

        assertThat(kline.bars()).isNotEmpty();
        assertThat(kline.bars().getLast().close()).isPositive();
    }

    @Test
    void fetchKline_sh930713_returnsEmptyForUnsupportedCsi() {
        IndexKline kline = TencentJsParser.parseIndexKline(
                client.fetchKlineRaw("sh930713", "2026-01-01", "2026-08-08"), "sh930713");

        assertThat(kline.bars()).isEmpty();
    }
}
