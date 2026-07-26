package com.fundpilot.backend.productcatalog.infrastructure.configuration;

import com.fundpilot.backend.productcatalog.infrastructure.remote.catalogsync.EastmoneyProductCatalogClient;
import com.fundpilot.backend.productcatalog.infrastructure.remote.feerefresh.EastmoneyFundFeeClient;
import feign.Client;
import feign.Feign;
import feign.Request;
import feign.Response;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProductCatalogRemoteConfiguration {
    private static final Bucket FEE_RATE_LIMITER = Bucket.builder().addLimit(Bandwidth.classic(20,
            Refill.intervally(20, Duration.ofSeconds(1)))).build();

    @Bean
    EastmoneyProductCatalogClient eastmoneyProductCatalogClient(
            @Value("${eastmoney.base-url:https://fund.eastmoney.com}") String baseUrl) {
        return Feign.builder()
                .requestInterceptor(request -> {
                    request.header("Referer", "https://fund.eastmoney.com/");
                    request.header("User-Agent", "Mozilla/5.0 FundPilot/1.0");
                })
                .options(new Request.Options(Duration.ofSeconds(1), Duration.ofSeconds(3), true))
                .target(EastmoneyProductCatalogClient.class, baseUrl);
    }

    @Bean
    EastmoneyFundFeeClient eastmoneyFundFeeClient(
            @Value("${eastmoney.fundf10-base-url:https://fundf10.eastmoney.com}") String baseUrl) {
        return Feign.builder()
                .client(new FeeRateLimitedClient())
                .requestInterceptor(request -> {
                    request.header("Referer", "https://fund.eastmoney.com/");
                    request.header("User-Agent", "Mozilla/5.0 FundPilot/1.0");
                })
                .options(new Request.Options(Duration.ofSeconds(1), Duration.ofSeconds(3), true))
                .target(EastmoneyFundFeeClient.class, baseUrl);
    }

    private static final class FeeRateLimitedClient implements Client {
        private final Client delegate = new Client.Default(null, null);

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            try {
                if (!FEE_RATE_LIMITER.asBlocking().tryConsume(1, Duration.ofSeconds(1))) {
                    throw new IOException("东方财富费率接口限流等待超过 1 秒");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("东方财富费率接口限流等待被中断", exception);
            }
            return delegate.execute(request, options);
        }
    }
}
