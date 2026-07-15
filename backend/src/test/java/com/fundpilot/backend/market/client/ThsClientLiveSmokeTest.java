package com.fundpilot.backend.market.client;

import feign.Feign;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
class ThsClientLiveSmokeTest {

    @Test
    void 同花顺净值和指数K线可用() {
        ThsClient fundClient = Feign.builder()
                .requestInterceptor(ThsClientConfig.requestInterceptor())
                .retryer(ThsClientConfig.retryer())
                .options(ThsClientConfig.options())
                .target(ThsClient.class, "https://fund.10jqka.com.cn");
        ThsIndexClient indexClient = Feign.builder()
                .requestInterceptor(ThsClientConfig.requestInterceptor())
                .retryer(ThsClientConfig.retryer())
                .options(ThsClientConfig.options())
                .target(ThsIndexClient.class, "https://d.10jqka.com.cn");

        var navs = ThsJsParser.parseNavHistory(
                fundClient.fetchUnitNavRaw("510300"),
                fundClient.fetchAccumulatedNavRaw("510300"));
        var kline = ThsJsParser.parseIndexKline(indexClient.fetchDailyKlineRaw("hs_1B0300"));

        assertThat(navs).isNotEmpty();
        assertThat(kline.bars()).hasSizeGreaterThan(20);
    }
}
