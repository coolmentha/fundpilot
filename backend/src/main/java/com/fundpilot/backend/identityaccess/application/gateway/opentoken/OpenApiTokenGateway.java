package com.fundpilot.backend.identityaccess.application.gateway.opentoken;

import java.util.Optional;

/** Token 明文仅穿越该网关用于一次签发/校验，领域与持久化层只接收摘要。 */
public interface OpenApiTokenGateway {

    String generate();

    Optional<Long> findOwnerId(String plaintext);
}
