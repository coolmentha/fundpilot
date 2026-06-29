package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #35 验收:东方财富数据源速率限流(Bucket4j 令牌桶),替换未接入的 Semaphore。
 * <p>每秒 N 次令牌,全客户端共享单例,防东方财富封 IP。
 * 纯函数语义测试(非阻塞 tryAcquire),避免计时抖动。
 */
class RateLimiterTest {

    @Test
    void 令牌充足时_tryAcquire_立即返回_true() {
        RateLimiter limiter = RateLimiter.perSecond(2);

        // 容量 2,首次连续 2 次应有令牌
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void 令牌耗尽后_tryAcquire_返回_false_被节流() {
        RateLimiter limiter = RateLimiter.perSecond(2);

        limiter.tryAcquire();
        limiter.tryAcquire();  // 容量 2 耗尽

        // 第 3 次无令牌,被节流(非阻塞返 false,不等下一个令牌)
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
