package com.fundpilot.backend.market.client;

import feign.Feign;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 同花顺 Feign 客户端配置，提供基金净值、基金字典和指数 K 线真实降级源。
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
        return Retryer.NEVER_RETRY;
    }

    public static Request.Options options() {
        return new Request.Options(Duration.ofSeconds(1), Duration.ofSeconds(3), true);
    }

    /**
     * 注册 {@link ThsClient} 为 Spring Bean，提供单位净值与累计净值原始响应。
     *
     * @param baseUrl 同花顺服务基础地址,由 {@code ths.base-url} 配置
     */
    @Bean
    public ThsClient thsClient(@Value("${ths.base-url:https://fund.10jqka.com.cn}") String baseUrl) {
        return Feign.builder()
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(ThsClient.class, baseUrl);
    }

    @Bean
    public ThsFundInfoClient thsFundInfoClient(
            @Value("${ths.fund-info-base-url:https://fund.10jqka.com.cn}") String baseUrl) {
        return Feign.builder()
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(ThsFundInfoClient.class, baseUrl);
    }

    @Bean
    public ThsIndexClient thsIndexClient(
            @Value("${ths.index-base-url:https://d.10jqka.com.cn}") String baseUrl) {
        return Feign.builder()
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(ThsIndexClient.class, baseUrl);
    }

    private ThsClientConfig() {
    }
}
