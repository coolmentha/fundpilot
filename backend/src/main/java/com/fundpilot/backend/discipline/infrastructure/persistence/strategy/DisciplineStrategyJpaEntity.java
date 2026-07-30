package com.fundpilot.backend.discipline.infrastructure.persistence.strategy;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import lombok.Getter; import lombok.Setter;
import org.hibernate.annotations.SQLDelete; import org.hibernate.annotations.SQLRestriction;
@Entity @Table(name = "discipline_strategy") @SQLRestriction("deleted_date IS NULL")
@SQLDelete(sql = "UPDATE discipline_strategy SET deleted_date = now() WHERE id = ? AND version = ?") @Getter @Setter
class DisciplineStrategyJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id; @Version private Long version;
    @Column(name="portfolio_fund_id") private Long portfolioFundId; @Column(name="owner_id") private Long ownerId;
    private String status; @Column(name="profit_activation_percent") private BigDecimal activation;
    @Column(name="stop_loss_pullback_percent") private BigDecimal pullback;
    @Column(name="profit_harvest_percent") private BigDecimal harvest;
    @Column(name="minimum_holding_percent") private BigDecimal minimumHolding;
    @Column(name="max_single_sell_percent") private BigDecimal maxSingleSell;
    @Column(name="cooldown_trading_days") private Integer cooldownDays;
    @Column(name="preset_fund_category") private String presetCategory;
    @Column(name="preset_version") private Integer presetVersion;
    private boolean customized;
    @Column(name="take_profit_phase") private String takeProfitPhase;
    @Column(name="cycle_started_at") private Instant cycleStartedAt;
    @Column(name="cycle_peak_nav") private BigDecimal cyclePeakNav;
    @Column(name="triggered_advice_id") private Long triggeredAdviceId;
    @Column(name="cooldown_started_at") private Instant cooldownStartedAt;
}
