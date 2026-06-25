package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:EastmoneyClient 共享 Semaphore(2) 实现限流。
 * <p>并发 3 个 acquire,第 3 个被阻塞直到前 2 个 release。
 */
class EastmoneyRateLimiterTest {

    @Test
    void permitsOnlyTwoConcurrentAcquires() throws Exception {
        Semaphore semaphore = EastmoneyClientConfig.semaphore();
        assertThat(semaphore.availablePermits()).isEqualTo(2);

        AtomicInteger acquired = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch blocker = new CountDownLatch(1);  // 让前 2 个先 acquire 再释放
        CountDownLatch allAcquired = new CountDownLatch(3);

        // 3 个线程同时 acquire
        for (int i = 0; i < 3; i++) {
            pool.submit(() -> {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                acquired.incrementAndGet();
                allAcquired.countDown();
                try {
                    blocker.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                semaphore.release();
            });
        }

        // 等一会让前 2 个 acquire 成功,第 3 个阻塞
        TimeUnit.MILLISECONDS.sleep(100);
        assertThat(semaphore.availablePermits()).isEqualTo(0);
        assertThat(acquired.get()).isEqualTo(2);

        // 放行,第 3 个应 acquire 到
        blocker.countDown();
        assertThat(allAcquired.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(acquired.get()).isEqualTo(3);

        pool.shutdown();
    }
}