package com.fundpilot.backend.market.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 指数 K 线缓存(日 K)。{@code MarketDataFetchService} 每日拉基准指数 K 线算 VolumeState 时顺便落库,
 * {@code KlineService} 读本地缓存渲染日/周/月 K——避免图表按需拉 push2his 触发 IP 限流。
 * <p>{@code indexCode} 用人类可读格式(如 {@code 930713.CSI}),与 {@code fund.benchmark_index_code} 一致。
 * 周/月 K 由 {@code KlineService} 在缓存日 K 上聚合,不单独存。
 */
@Entity
@Table(name = "index_kline",
        indexes = {
                @Index(name = "idx_index_kline_code", columnList = "index_code"),
        })
@SQLDelete(sql = "UPDATE index_kline SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class IndexKlineEntity extends AbstractEntity {

    /** 指数代码(人类可读,如 930713.CSI / 000300.SH),与 fund.benchmark_index_code 一致。 */
    private String indexCode;

    /** 交易日(UTC 0 点 Instant 表当日,对齐 InstantDateConverter 约定)。 */
    private Instant tradeDate;

    private BigDecimal open;

    private BigDecimal high;

    private BigDecimal low;

    private BigDecimal close;

    private Long volume;
}
