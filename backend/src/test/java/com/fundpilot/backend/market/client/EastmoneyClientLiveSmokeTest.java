package com.fundpilot.backend.market.client;

import feign.Feign;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EastmoneyClient 真实东方财富服务 live smoke 测试。
 * <p>{@code @Tag("live")} 确保 {@code mvnw verify} 默认排除,
 * 通过 {@code mvnw verify -Plive} 触发。
 *
 * @see EastmoneyClientConfig
 */
@Tag("live")
class EastmoneyClientLiveSmokeTest {

    private final EastmoneyClient client = Feign.builder()
            .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
            .target(EastmoneyClient.class, "https://fund.eastmoney.com/");

    /** 指数 K 线在 push2his.eastmoney.com 独立域名,见 {@link EastmoneyKlineClient}。 */
    private final EastmoneyKlineClient klineClient = Feign.builder()
            .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
            .target(EastmoneyKlineClient.class, "https://push2his.eastmoney.com/");

    @Test
    void fetchNavHistory_from510300_lastDateWithin7Days() {
        var snapshots = client.fetchNavHistory("510300");

        assertThat(snapshots).isNotEmpty();
        Instant lastDate = snapshots.getLast().navDate();
        assertThat(lastDate).isAfter(Instant.now().minus(7, ChronoUnit.DAYS));
    }

    @Test
    void fetchFundDict_containsMoreThan15000Funds() {
        var dict = client.fetchFundDict();

        assertThat(dict).size().isGreaterThan(15000);
        assertThat(dict.getFirst().fundCode()).isNotBlank();
    }

    @Test
    void fetchIndexKline_forSh300_returnsAtLeastOneBar() {
        // 沪深300 secid=1.000300(沪市前缀 1.),lmt 取近 5 根日 K
        var kline = EastmoneyJsParser.parseIndexKline(klineClient.fetchKlineRaw("1.000300", "5"));

        assertThat(kline.bars()).isNotEmpty();
        assertThat(kline.bars().getFirst().close()).isPositive();
    }
}