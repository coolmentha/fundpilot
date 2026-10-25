package com.fundpilot.backend.identityaccess.adapter.api.opentoken;

import com.fundpilot.backend.identityaccess.application.query.opentoken.OpenApiTokenQueryHandler;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 供 Open API 入站过滤器解析 Token 归属，不暴露 Token 管理能力。 */
@Component
@RequiredArgsConstructor
public class OpenApiTokenApi {

    private final OpenApiTokenQueryHandler queries;

    public Optional<Long> findOwnerId(String plaintext) {
        return queries.findOwnerId(plaintext);
    }

    public void markUsed(String plaintext, Instant now) {
        queries.markUsed(plaintext, now);
    }
}
