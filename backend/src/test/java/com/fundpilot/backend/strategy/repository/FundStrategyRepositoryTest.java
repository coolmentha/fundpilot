package com.fundpilot.backend.strategy.repository;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #7 循环 E:{@code FundStrategyRepository.findEffectiveFundIds} 查所有
 * {@code status = EFFECTIVE} 的 fund_id 去重列表,供 {@code MarketDataFetchJob} 决定拉取范围。
 */
class FundStrategyRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    FundStrategyRepository fundStrategyRepository;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void findEffectiveFundIds_只返回_EFFECTIVE_的_fund_id() {
        FundEntity fundA = fundRepository.save(newFund("161725"));
        FundEntity fundB = fundRepository.save(newFund("161726"));
        FundEntity fundC = fundRepository.save(newFund("161727"));

        // A: EFFECTIVE(应返回)
        fundStrategyRepository.save(strategy(fundA, StrategyParamStatus.EFFECTIVE));
        // B: PENDING_CALIBRATION(不应返回)
        fundStrategyRepository.save(strategy(fundB, StrategyParamStatus.PENDING_CALIBRATION));
        // C: CALIBRATED(不应返回)
        fundStrategyRepository.save(strategy(fundC, StrategyParamStatus.CALIBRATED));

        List<Long> fundIds = fundStrategyRepository.findEffectiveFundIds();

        assertThat(fundIds).hasSize(1);
        assertThat(fundIds).containsExactly(fundA.getId());
    }

    @Test
    @Transactional
    void findEffectiveFundIds_无_EFFECTIVE_返回空列表() {
        List<Long> fundIds = fundStrategyRepository.findEffectiveFundIds();

        assertThat(fundIds).isEmpty();
    }

    private FundEntity newFund(String code) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setFundName("测试基金-" + code);
        return fund;
    }

    private static FundStrategyEntity strategy(FundEntity fund, StrategyParamStatus status) {
        FundStrategyEntity entity = new FundStrategyEntity();
        entity.setFundEntity(fund);
        entity.setStatus(status);
        return entity;
    }
}
