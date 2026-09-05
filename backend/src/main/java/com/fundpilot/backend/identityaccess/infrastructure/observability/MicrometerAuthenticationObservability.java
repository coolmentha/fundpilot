package com.fundpilot.backend.identityaccess.infrastructure.observability;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.AuthenticationObservability;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrometerAuthenticationObservability implements AuthenticationObservability {

    private static final Logger log = LoggerFactory.getLogger(MicrometerAuthenticationObservability.class);

    private final MeterRegistry meterRegistry;

    @Override
    public void loginSucceeded(String source, String normalizedUsername) {
        meterRegistry.counter("auth_login_success_total").increment();
    }

    @Override
    public void loginFailed(String source, String normalizedUsername, String reason) {
        meterRegistry.counter("auth_login_failures_total", "reason", safeReason(reason)).increment();
        log.warn("身份认证失败 source={} subject={} result=failure reason={}",
                safeSource(source), subject(normalizedUsername), safeReason(reason));
    }

    @Override
    public void loginRateLimited(String source, String normalizedUsername, long retryAfterSeconds) {
        meterRegistry.counter("auth_login_rate_limited_total").increment();
        meterRegistry.counter("auth_login_lockouts_total").increment();
        log.warn("身份认证锁定 source={} subject={} result=blocked reason=rate_limit retry_after_seconds={}",
                safeSource(source), subject(normalizedUsername), Math.max(1, retryAfterSeconds));
    }

    @Override
    public void abnormalTraffic(String source, String normalizedUsername, String reason) {
        meterRegistry.counter("auth_login_anomalous_traffic_total", "reason", safeReason(reason)).increment();
        log.warn("身份认证异常流量 source={} subject={} result=blocked reason={}",
                safeSource(source), subject(normalizedUsername), safeReason(reason));
    }

    private String subject(String normalizedUsername) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((normalizedUsername == null ? "" : normalizedUsername)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成认证主体标识", exception);
        }
    }

    private String safeSource(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        return source.replace('\r', '_').replace('\n', '_');
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified";
        }
        return reason.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
