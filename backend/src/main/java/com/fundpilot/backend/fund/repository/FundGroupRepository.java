package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FundGroupRepository extends JpaRepository<FundGroupEntity, Long> {
    List<FundGroupEntity> findAllByOrderBySortOrderAscIdAsc();

    List<FundGroupEntity> findAllByOwnerIdOrderBySortOrderAscIdAsc(Long ownerId);

    @Modifying
    @Query("update FundGroupEntity g set g.ownerId = :ownerId where g.ownerId is null")
    int claimUnowned(@Param("ownerId") Long ownerId);

    Optional<FundGroupEntity> findByNameIgnoreCase(String name);

    @Modifying
    @Query(value = "DELETE FROM fund_group_member WHERE group_id IN (:groupIds)", nativeQuery = true)
    void deleteMemberships(@Param("groupIds") Set<Long> groupIds);
}
