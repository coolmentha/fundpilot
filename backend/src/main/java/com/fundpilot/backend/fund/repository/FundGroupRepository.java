package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FundGroupRepository extends JpaRepository<FundGroupEntity, Long> {
    List<FundGroupEntity> findAllByOrderBySortOrderAscIdAsc();

    Optional<FundGroupEntity> findByNameIgnoreCase(String name);
}
