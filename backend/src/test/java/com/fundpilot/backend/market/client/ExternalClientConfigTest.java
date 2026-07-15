package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalClientConfigTest {

    @Test
    void eastmoneyOptions_使用一秒连接三秒读取超时() {
        var options = EastmoneyClientConfig.options();

        assertThat(options.connectTimeoutMillis()).isEqualTo(1_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3_000);
    }

    @Test
    void thsOptions_使用一秒连接三秒读取超时() {
        var options = ThsClientConfig.options();

        assertThat(options.connectTimeoutMillis()).isEqualTo(1_000);
        assertThat(options.readTimeoutMillis()).isEqualTo(3_000);
    }
}
