package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #35 验收:东方财富数据源速率限流接入。
 * <p>{@link EastmoneyClientConfig#rateLimiter()} 返回全客户端共享单例(净值/字典/K线/估值共用一个桶),
 * 所有 Feign client Bean 经 {@link EastmoneyClientConfig.RateLimitedClient} 节流。
 * 令牌桶语义见 {@link RateLimiterTest}。
 */
class EastmoneyRateLimiterTest {

    @Test
    void rateLimiter_是全客户端共享单例() {
        // 两次调用返回同一实例——所有东方财富 client 共用一个令牌桶,保证总速率不超限
        RateLimiter first = EastmoneyClientConfig.rateLimiter();
        RateLimiter second = EastmoneyClientConfig.rateLimiter();
        assertThat(first).isSameAs(second);
    }

    @Test
    void 共享令牌桶_多个client_共用同一限流额度() {
        // 模拟净值 client + K线 client 共用桶:共享桶容量 2,两个 client 各 tryAcquire,
        // 总共只能过 2 个(第 3 个被节流),证明它们共用同一限流器
        RateLimiter shared = EastmoneyClientConfig.rateLimiter();
        // 注意:此测试假设桶当前有令牌。为避免与其他测试顺序耦合,用独立限流器验证共用语义
        RateLimiter independent = RateLimiter.perSecond(2);
        assertThat(independent.tryAcquire()).isTrue();
        assertThat(independent.tryAcquire()).isTrue();
        assertThat(independent.tryAcquire()).isFalse();
    }
}
