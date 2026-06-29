package com.fundpilot.backend.market.client;

import feign.Feign;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ThsClient(同花顺)的 Feign 配置,作为东方财富的降级数据源(issue #7)。
 * <p>加同花顺 Referer/UA 请求头;不重试(让 {@link MarketDataSourceChain} 控制降级)。
 * <p>Feign {@code url} 通过 {@code ths.base-url} 配置,默认指向同花顺服务。
 */
@Configuration(proxyBeanMethods = false)
public class ThsClientConfig {

    /** 请求头拦截器:加同花顺 Referer + 合理 User-Agent。 */
    public static RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Referer", "https://fund.10jqka.com.cn/");
            requestTemplate.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        };
    }

    /** 默认不重试(让降级链控制)。 */
    public static Retryer retryer() {
        return new Retryer.Default(100, 1000, 0);
    }

    /**
     * 注册 {@link ThsClient} 为 Spring Bean,供 {@code MarketDataSourceChain} 降级使用。
     *
     * @param baseUrl 同花顺服务基础地址,由 {@code ths.base-url} 配置
     */
    @Bean
    public ThsClient thsClient(@Value("${ths.base-url:https://fund.10jqka.com.cn}") String baseUrl) {
        return Feign.builder()
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .target(ThsClient.class, baseUrl);
    }

    private ThsClientConfig() {
    }
}
