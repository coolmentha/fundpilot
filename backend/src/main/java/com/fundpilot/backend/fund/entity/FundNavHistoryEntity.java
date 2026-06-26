package com.fundpilot.backend.fund.entity;

import com.fundpilot.backend.common.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fund_nav_history")
@SQLDelete(sql = "UPDATE fund_nav_history SET deleted_date = now() WHERE id = ? AND version = ?")
@Getter
@Setter
public class FundNavHistoryEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private FundEntity fundEntity;

    private Instant navDate;

    private BigDecimal nav;

    private BigDecimal accumulatedNav;

}
