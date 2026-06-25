package com.fundpilot.backend.market.client;

import feign.Feign;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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

    @Test
    void fetchNavHistory_from510300_lastDateWithin7Days() {
        var snapshots = client.fetchNavHistory("510300");

        assertThat(snapshots).isNotEmpty();
        LocalDate lastDate = snapshots.getLast().navDate();
        assertThat(lastDate).isAfter(LocalDate.now().minusDays(7));
    }

    @Test
    void fetchFundDict_containsMoreThan15000Funds() {
        var dict = client.fetchFundDict();

        assertThat(dict).size().isGreaterThan(15000);
        assertThat(dict.getFirst().fundCode()).isNotBlank();
    }

    @Test
    void fetchIndexKline_forSh300_returnsAtLeastOneBar() {
        // 沪深300 K 线,近一周
        var kline = client.fetchIndexKline("000300", "week");

        assertThat(kline.bars()).isNotEmpty();
        assertThat(kline.bars().getFirst().close()).isPositive();
    }
}