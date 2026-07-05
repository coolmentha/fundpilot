package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundLotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FundLotRepository extends JpaRepository<FundLotEntity, Long> {

    /**
     * FIFO 匹配:查某基金所有剩余份额 > 0 的 lot,按 acquire_date 升序(先买入先卖出)。
     * 供 {@code TransactionConfirmSupport.onSellConfirmed} 遍历消耗。
     */
    @Query("select l from FundLotEntity l where l.fundEntity.id = :fundId and l.remainingShares > 0 order by l.acquireDate asc")
    List<FundLotEntity> findOpenLotsByFundIdOrderByAcquireDateAsc(@Param("fundId") Long fundId);

    /** 查某基金全部 lot(历史回填校验 / 归档级联用)。 */
    List<FundLotEntity> findByFundEntity_Id(Long fundId);
}
