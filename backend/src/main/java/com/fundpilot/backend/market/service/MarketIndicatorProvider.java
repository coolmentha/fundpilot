package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;

import java.time.Instant;
import java.util.Optional;

/**
 * 行情指标读取接口(issue #7 要求):从 snapshot 表读取单日指标,
 * 供 #12 SignalGenerationJob 信号引擎使用。读不到时返回 {@link Optional#empty()},
 * 上层据此出 {@code signalType=NONE, reason=INSUFFICIENT_MARKET_DATA}。
 */
public interface MarketIndicatorProvider {

    Optional<MarketIndicatorSnapshotEntity> getIndicators(Long fundId, Instant date);
}
