package com.fundpilot.backend.common;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
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
    FundRepository fundRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE fund CASCADE");
    }

    @Test
    void 失败单元回滚后_后续独立单元仍可提交() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("TX" + System.nanoTime());
        fund.setFundName("原名称");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setStatus(FundStatus.PENDING_HOLDING);
        Long fundId = fundRepository.save(fund).getId();

        assertThatThrownBy(() -> executor.execute(() -> {
            FundEntity current = fundRepository.findById(fundId).orElseThrow();
            current.setFundName("应回滚");
            fundRepository.saveAndFlush(current);
            throw new IllegalStateException("模拟单基金失败");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fundRepository.findById(fundId).orElseThrow().getFundName()).isEqualTo("原名称");

        executor.execute(() -> {
            FundEntity current = fundRepository.findById(fundId).orElseThrow();
            current.setFundName("已提交");
            return fundRepository.save(current);
        });

        assertThat(fundRepository.findById(fundId).orElseThrow().getFundName()).isEqualTo("已提交");
    }
}
