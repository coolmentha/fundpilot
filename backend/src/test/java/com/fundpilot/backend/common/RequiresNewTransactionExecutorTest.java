package com.fundpilot.backend.platform.transaction;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiresNewTransactionExecutorTest extends AbstractIntegrationTest {

    @Autowired
    RequiresNewTransactionExecutor executor;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE fund_product CASCADE");
    }

    @Test
    void 失败单元回滚后_后续独立单元仍可提交() {
        Long productId = jdbcTemplate.queryForObject("""
                INSERT INTO fund_product (fund_code, fund_name)
                VALUES (?, '原名称')
                RETURNING id
                """, Long.class, "TX" + Long.toUnsignedString(System.nanoTime(), 36));

        assertThatThrownBy(() -> executor.execute(() -> {
            jdbcTemplate.update("UPDATE fund_product SET fund_name = '应回滚' WHERE id = ?", productId);
            throw new IllegalStateException("模拟单基金失败");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(nameOf(productId)).isEqualTo("原名称");

        executor.execute(() -> {
            jdbcTemplate.update("UPDATE fund_product SET fund_name = '已提交' WHERE id = ?", productId);
            return null;
        });

        assertThat(nameOf(productId)).isEqualTo("已提交");
    }

    private String nameOf(long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT fund_name FROM fund_product WHERE id = ?", String.class, productId);
    }
}
