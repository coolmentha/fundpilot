package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.IndexKlineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IndexKlineRepository extends JpaRepository<IndexKlineEntity, Long> {

    /** 查某指数全部日 K(升序),供 KlineService 渲染日 K 或聚合周/月 K。软删行由 @SQLRestriction 过滤。 */
    List<IndexKlineEntity> findByIndexCodeOrderByTradeDateAsc(String indexCode);

    /** 查某指数已落库的交易日集合,供每日同步按 index_code+trade_date 去重(只插缺失)。 */
    @Query("select e.tradeDate from IndexKlineEntity e where e.indexCode = :indexCode")
    List<Instant> findTradeDatesByIndexCode(@Param("indexCode") String indexCode);
}
