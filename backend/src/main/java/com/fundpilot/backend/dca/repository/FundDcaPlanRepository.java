package com.fundpilot.backend.dca.repository;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FundDcaPlanRepository extends JpaRepository<FundDcaPlanEntity, Long> {

    /**
     * 查所有 {@code status = EFFECTIVE} 的 fund_id 去重列表(DcaSuggestionJob 据此遍历)。
     */
    @Query("select distinct p.fundEntity.id from FundDcaPlanEntity p where p.status = :status")
    List<Long> findFundIdsByStatus(@Param("status") DcaPlanStatus status);

    default List<Long> findEffectiveFundIds() {
        return findFundIdsByStatus(DcaPlanStatus.EFFECTIVE);
    }

    List<FundDcaPlanEntity> findByFundEntity_Id(Long fundId);

    Optional<FundDcaPlanEntity> findByFundEntity_IdAndStatus(Long fundId, DcaPlanStatus status);

    List<FundDcaPlanEntity> findByStatusAndEnabledTrue(DcaPlanStatus status);

    @Query("select p from FundDcaPlanEntity p join fetch p.fundEntity")
    List<FundDcaPlanEntity> findAllWithFund();
}
