package com.fundpilot.backend.signal.repository;

import com.fundpilot.backend.signal.entity.SignalLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SignalLogRepository extends JpaRepository<SignalLogEntity, Long> {

    /**
     * 查某基金某日(UTC 0点起 24 小时区间)已存在的 SignalLog(用于 #13 重跑覆盖:软删旧 + 写新)。
     * 唯一约束 {@code uq_signal_log_daily} 按 signal_date::date 去重,这里用区间查整日。
     */
    List<SignalLogEntity> findByFundEntity_IdAndSignalDateBetween(Long fundId, Instant dayStart, Instant dayEnd);
}
