package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FundRepository extends JpaRepository<FundEntity, Long> {
    List<FundEntity> findAllByOwnerId(Long ownerId);

    Optional<FundEntity> findByIdAndOwnerId(Long id, Long ownerId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("update FundEntity f set f.ownerId = :ownerId where f.ownerId is null")
    int claimUnowned(@Param("ownerId") Long ownerId);

    /**
     * 查所有 fundSubType 为 null 的 legacy 行。
     */
    List<FundEntity> findByFundSubTypeIsNull();

    /**
     * 查指定状态的基金(issue #18 概览页盈亏聚合用,取所有 HOLDING 基金)。
     */
    List<FundEntity> findByStatus(FundStatus status);

    List<FundEntity> findByStatusAndOwnerId(FundStatus status, Long ownerId);

    Optional<FundEntity> findByFundCode(String fundCode);

    Optional<FundEntity> findByFundCodeAndOwnerId(String fundCode, Long ownerId);

    /** 锁定基金行，串行化依赖事实持仓校验的手工调整。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FundEntity f where f.id = :id")
    Optional<FundEntity> findByIdForUpdate(@Param("id") Long id);
}
