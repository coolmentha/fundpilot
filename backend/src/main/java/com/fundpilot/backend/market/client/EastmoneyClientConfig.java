package com.fundpilot.backend.market.client;

import feign.Client;
import feign.RequestInterceptor;
import feign.Request;
import feign.Response;
import feign.Retryer;
import feign.Feign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * EastmoneyClient 的 Feign 配置:速率限流 + Referer/UA 请求头拦截器。
 * <p>ADR-0002:东方财富对 IP 有限速(约每秒 2-3 次),用 {@link RateLimiter}(Bucket4j 令牌桶)
 * 做速率限流(issue #35 替换未接入的 Semaphore);加 {@code Referer: https://fund.eastmoney.com/} 避免被反爬。
 * <p>限流为全客户端共享单例(净值/字典/K线/估值共用一个桶),保证总请求速率不超限。
 * 静态工厂方法保留供单元测试直接使用;{@link #eastmoneyClient(String)} 等注册为 Spring Bean,
 * 供业务组件注入,base URL 通过 {@code eastmoney.base-url} 配置。
 */
@Configuration(proxyBeanMethods = false)
public class EastmoneyClientConfig {

    /** 东方财富 IP 限速约每秒 2-3 次,统一取 2 次/秒(全客户端共享一个桶)。 */
    private static final long PERMITS_PER_SECOND = 2;
    /** 共享速率限流器,全客户端单例。 */
    private static final RateLimiter SHARED_LIMITER = RateLimiter.perSecond(PERMITS_PER_SECOND);

    public static RateLimiter rateLimiter() {
        return SHARED_LIMITER;
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
     * 注册 {@link EastmoneyClient} 为 Spring Bean(fund.eastmoney.com 域名,净值+字典)。
     * 请求经 {@link RateLimitedClient} 节流(共享令牌桶),防东方财富封 IP。
     *
     * @param baseUrl 东方财富服务基础地址,由 {@code eastmoney.base-url} 配置,默认指向官方域名
     */
    @Bean
    public EastmoneyClient eastmoneyClient(@Value("${eastmoney.base-url:https://fund.eastmoney.com}") String baseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .target(EastmoneyClient.class, baseUrl);
    }

    /**
     * 注册 {@link EastmoneyKlineClient} 为 Spring Bean(push2his.eastmoney.com 域名,指数 K 线)。
     * K 线接口与基金净值不同域名,故独立 target;共享同一限流桶。
     */
    @Bean
    public EastmoneyKlineClient eastmoneyKlineClient(
            @Value("${eastmoney.kline-base-url:https://push2his.eastmoney.com}") String klineBaseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .target(EastmoneyKlineClient.class, klineBaseUrl);
    }

    /**
     * 注册 {@link EastmoneyFundGzClient} 为 Spring Bean(fundgz.1234567.com.cn 域名,盘中估值)。
     * 估值接口在第三个域名,故独立 target;共享同一限流桶。返回 JSONP 由 parser 剥外壳解析。
     */
    @Bean
    public EastmoneyFundGzClient eastmoneyFundGzClient(
            @Value("${eastmoney.gz-base-url:https://fundgz.1234567.com.cn}") String gzBaseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .target(EastmoneyFundGzClient.class, gzBaseUrl);
    }

    /**
     * 注册 {@link MarketDataSource} 降级链为 Spring Bean,供业务组件注入。
     * <p>降级顺序:东方财富(主,聚合 fund+push2his 两域名) → 同花顺(兜底);
     * 全失败抛 {@code MARKET_DATA_ALL_SOURCES_FAILED}。
     *
     * @param eastmoney 东方财富数据源(主,聚合净值/字典/K线)
     * @param thsClient 同花顺数据源(兜底)
     */
    @Bean
    public MarketDataSource marketDataSource(EastmoneyMarketDataSource eastmoney, ThsClient thsClient) {
        return new MarketDataSourceChain(java.util.List.of(eastmoney, thsClient));
    }

    private EastmoneyClientConfig() {
    }

    /**
     * Feign Client 包装:每个请求前 {@link RateLimiter#acquire()} 节流(阻塞等令牌),
     * 保证所有东方财富数据线(净值/字典/K线/估值)总速率不超每秒 2 次。
     */
    static final class RateLimitedClient implements Client {
        private final Client delegate = new Client.Default(null, null);
        private final RateLimiter rateLimiter;

        RateLimitedClient(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
        }

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            rateLimiter.acquire();
            return delegate.execute(request, options);
        }
    }
}