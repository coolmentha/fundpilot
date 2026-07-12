package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundLotRedemptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundLotRedemptionRepository extends JpaRepository<FundLotRedemptionEntity, Long> {

    /** 查某笔卖出交易消耗的所有 lot 明细(前端展示赎回费明细用)。 */
    List<FundLotRedemptionEntity> findBySellTxId(Long sellTxId);

    /** 查某 lot 的所有被消耗记录(校验 remaining_shares 一致性用)。 */
    List<FundLotRedemptionEntity> findByLotId(Long lotId);

    List<FundLotRedemptionEntity> findByLotIdIn(List<Long> lotIds);

    List<FundLotRedemptionEntity> findBySellTxIdIn(List<Long> transactionIds);
}
