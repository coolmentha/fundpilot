package com.fundpilot.backend.discipline.infrastructure.persistence.classification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "discipline_classification")
@Getter
@Setter
class DisciplineClassificationJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_fund_id")
    private Long portfolioFundId;

    @Column(name = "owner_id")
    private Long ownerId;

    private String category;

    private String source;
}
