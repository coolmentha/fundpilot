package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.IndexKlineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndexKlineRepository extends JpaRepository<IndexKlineEntity, Long> {

    boolean existsByIndexCode(String indexCode);

    /** 查某指数全部日 K(升序),供 KlineService 渲染日 K 或聚合周/月 K。软删行由 @SQLRestriction 过滤。 */
    List<IndexKlineEntity> findByIndexCodeOrderByTradeDateAsc(String indexCode);

}
