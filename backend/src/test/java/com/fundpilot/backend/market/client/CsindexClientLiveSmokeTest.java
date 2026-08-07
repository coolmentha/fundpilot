package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Feign;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CsindexClient 真实中证指数公司服务 live smoke 测试。
 * <p>{@code @Tag("live")} 确保 {@code mvn verify} 默认排除,通过 {@code mvn verify -Plive} 触发。
 * <p>验证借鉴 akshare 的 csindex.com.cn 接口可从本机拉到 930713(中证人工智能)日线——
 * 替代被 VPS IP 限流的 push2his。
 */
@Tag("live")
class CsindexClientLiveSmokeTest {

    private final CsindexClient client = Feign.builder()
            .requestInterceptor(EastmoneyClientConfig.requestInterceptor())
            .target(CsindexClient.class, "https://www.csindex.com.cn");

    @Test
    void fetchIndexPerf_csi930713_returnsDailyBars() {
        String raw = client.fetchIndexPerf("930713", "20260101", "20260704");

        IndexKline kline = CsindexJsParser.parseIndexKline(raw, "930713");

        assertThat(kline.bars()).isNotEmpty();
        IndexKline.Bar first = kline.bars().getFirst();
        assertThat(first.close()).isPositive();
        assertThat(first.open()).isNotNull();
        assertThat(first.high()).isNotNull();
        assertThat(first.low()).isNotNull();
        assertThat(first.volume()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void fetchIndexPerf_csi300_000300_returnsDailyBars() {
        // 沪深 300 在中证公司编制范围(000300),验证覆盖沪市中证编制指数
        String raw = client.fetchIndexPerf("000300", "20260601", "20260704");

        IndexKline kline = CsindexJsParser.parseIndexKline(raw, "000300");

        assertThat(kline.bars()).isNotEmpty();
        assertThat(kline.bars().getFirst().close()).isPositive();
    }

    @Test
    void fetchIndexPerf_sz399006_throws_to_让链回退() {
        // 深交所创业板指不在中证编制范围 → 空 data → 抛异常(降级链继续后续源)
        String raw = client.fetchIndexPerf("399006", "20260601", "20260704");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> CsindexJsParser.parseIndexKline(raw, "399006"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("399006");
    }
}
