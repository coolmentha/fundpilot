package com.fundpilot.backend.strategy.repository;

import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FundStrategyRepository extends JpaRepository<FundStrategyEntity, Long> {

    /**
     * 查所有 {@code status = EFFECTIVE} 的 fund_id 去重列表。
     * {@code MarketDataFetchJob} 据此决定每日拉取行情指标的基金范围。
     */
    @Query("select distinct fs.fundEntity.id from FundStrategyEntity fs where fs.status = :status")
    List<Long> findFundIdsByStatus(@Param("status") StrategyParamStatus status);

    /**
     * 便捷方法:返回所有 EFFECTIVE 策略对应的 fund_id 去重列表。
     */
    default List<Long> findEffectiveFundIds() {
        return findFundIdsByStatus(StrategyParamStatus.EFFECTIVE);
    }
}