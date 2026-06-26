package com.fundpilot.backend.signal.repository;

import com.fundpilot.backend.signal.entity.SignalLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SignalLogRepository extends JpaRepository<SignalLogEntity, Long> {

    /**
     * 查某基金某日(UTC 0点起 24 小时区间)已存在的 SignalLog(用于 #13 重跑覆盖:软删旧 + 写新)。
     * 唯一约束 {@code uq_signal_log_daily} 按 signal_date::date 去重,这里用区间查整日。
     */
    List<SignalLogEntity> findByFundEntity_IdAndSignalDateBetween(Long fundId, Instant dayStart, Instant dayEnd);

    /**
     * 查某基金全部信号日志(归档级联软删用,无日期范围以避免区间端点溢出)。
     */
    List<SignalLogEntity> findByFundEntity_Id(Long fundId);

    /**
     * 查某基金最新一条信号(issue #16 GET /api/funds/{fundId}/signals/today 用,取当日最新)。
     */
    Optional<SignalLogEntity> findTopByFundEntity_IdOrderBySignalDateDesc(Long fundId);

    /**
     * 查所有基金最新信号(issue #16 GET /api/signals/pending 跨基金未回应信号工作台用)。
     * 取每只基金最新一行(子查询或 native 更精确,本期用全量倒序取前 100 简化)。
     */
    List<SignalLogEntity> findTop100ByOrderBySignalDateDesc();
}
