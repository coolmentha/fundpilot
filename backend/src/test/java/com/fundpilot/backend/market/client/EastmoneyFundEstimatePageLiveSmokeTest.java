package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Feign;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实东方财富静态估值页只读冒烟测试。 */
@Tag("live")
class EastmoneyFundEstimatePageLiveSmokeTest {

    @Test
    void fetchFirstPage_当天有可解析估值数据() {
        EastmoneyFundEstimatePageClient client = Feign.builder()
                .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
                .retryer(EastmoneyClientConfig.retryer())
                .options(EastmoneyClientConfig.options())
                .target(EastmoneyFundEstimatePageClient.class, "https://fund.eastmoney.com/");

        var rows = EastmoneyFundEstimatePageParser.parse(client.fetchPageRaw(1));

        assertThat(rows).isNotEmpty();
        assertThat(rows.values()).allMatch(row -> row.estimateDate()
                .equals(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()));
    }
}
