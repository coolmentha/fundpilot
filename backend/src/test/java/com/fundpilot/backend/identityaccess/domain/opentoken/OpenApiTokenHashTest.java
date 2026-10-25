package com.fundpilot.backend.identityaccess.domain.opentoken;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiTokenHashTest {

    @Test
    void 相同明文生成稳定SHA256摘要且不包含明文() {
        var first = OpenApiTokenHash.of("fp_example-token");
        var second = OpenApiTokenHash.of("fp_example-token");

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).hasSize(64).matches("[0-9a-f]{64}")
                .doesNotContain("fp_example-token");
    }

    @Test
    void 拒绝非法摘要() {
        assertThatThrownBy(() -> new OpenApiTokenHash("not-a-sha256"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
