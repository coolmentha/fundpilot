package com.fundpilot.backend.integration.yangjibao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YangjibaoSignerTest {
    private final YangjibaoSigner signer = new YangjibaoSigner();

    @Test
    void anonymousSignatureUsesRawPathAndIgnoresQuery() {
        String withoutQuery = signer.anonymous("/qr_code", 1784650158L, "secret");
        assertThat(signer.anonymous("/qr_code?ignored=true", 1784650158L, "secret"))
                .isEqualTo(withoutQuery);
        assertThat(withoutQuery).hasSize(32);
    }

    @Test
    void authenticatedSignatureDiffersFromAnonymousSignature() {
        assertThat(signer.authenticated("/user_account", "token", 1784650158L, "secret"))
                .isEqualTo("783d3c1dbd1fb40d87e8a432e6067c12");
    }
}
