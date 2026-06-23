package com.fundpilot.backend.signal.repository;

import com.fundpilot.backend.signal.entity.SignalLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalLogRepository extends JpaRepository<SignalLogEntity, Long> {
}