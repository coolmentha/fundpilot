package com.fundpilot.backend.market.client;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;

/**
 * 东方财富数据源速率限流(issue #35):Bucket4j 令牌桶,替换未接入的 Semaphore。
 * <p>每秒 N 次令牌,全客户端共享单例,防东方财富封 IP。应用于所有东方财富数据线
 * (净值/字典/K线/估值)的 Feign client 请求层——请求前 {@link #tryAcquire()} 节流,
 * 令牌不足时阻塞等下一个令牌(由调用方决定用 tryAcquire 非阻塞或 acquire 阻塞)。
 *
 * <p>令牌桶语义:容量 = 每秒令牌数(允许瞬时并发到容量),每秒匀速补充。
 * 与原 Semaphore(2)(并发数=2)不同,这是速率限流(每秒 N 次),更贴合东方财富 IP 限速。
 */
public final class RateLimiter {

    private final Bucket bucket;

    private RateLimiter(Bucket bucket) {
        this.bucket = bucket;
    }

    /**
     * @param permitsPerSecond 每秒允许的请求数(令牌补充速率与容量)
     * @return 共享单例限流器
     */
    public static RateLimiter perSecond(long permitsPerSecond) {
        Bandwidth limit = Bandwidth.classic(permitsPerSecond,
                Refill.intervally(permitsPerSecond, Duration.ofSeconds(1)));
        return new RateLimiter(Bucket.builder().addLimit(limit).build());
    }

    /** 非阻塞:有令牌返 true 并消费 1 个;无令牌返 false(被节流)。 */
    public boolean tryAcquire() {
        return bucket.tryConsume(1);
    }

    /** 阻塞:等下一个令牌可用再消费(用于请求前强制节流,保证不超速)。 */
    public void acquire() {
        try {
            bucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("限流等待被中断", e);
        }
    }
}
