package com.fundpilot.backend.market.client;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.Feign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;

/**
 * EastmoneyClient 的 Feign 配置:限流 Semaphore + Referer/UA 请求头拦截器。
 * <p>ADR-0002:东方财富对 IP 有限速(约每秒 2-3 次),用 {@link Semaphore}(2) 做节流;
 * 加 {@code Referer: https://fund.eastmoney.com/} 避免被反爬。
 * <p>静态工厂方法保留供单元测试直接使用;{@link #eastmoneyClient(String)} 注册为 Spring Bean,
 * 供 {@code MarketDataFetchService} 等业务组件注入,base URL 通过 {@code eastmoney.base-url} 配置。
 */
@Configuration(proxyBeanMethods = false)
public class EastmoneyClientConfig {

    /** 共享限流信号量,全客户端最多 2 个并发请求。单例,所有 Feign 实例共享。 */
    private static final Semaphore SEMAPHORE = new Semaphore(2);

    public static Semaphore semaphore() {
        return SEMAPHORE;
    }

    /** 请求头拦截器:加 Referer + 合理 User-Agent。 */
    public static RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Referer", "https://fund.eastmoney.com/");
            requestTemplate.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        };
    }

    /** 默认不重试(让调用方控制降级策略)。 */
    public static Retryer retryer() {
        return new Retryer.Default(100, 1000, 0);
    }

    /**
     * 注册 {@link EastmoneyClient} 为 Spring Bean,供业务组件注入。
     *
     * @param baseUrl 东方财富服务基础地址,由 {@code eastmoney.base-url} 配置,默认指向官方域名
     */
    @Bean
    public EastmoneyClient eastmoneyClient(@Value("${eastmoney.base-url:https://fund.eastmoney.com}") String baseUrl) {
        return Feign.builder()
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .target(EastmoneyClient.class, baseUrl);
    }

    private EastmoneyClientConfig() {
    }
}