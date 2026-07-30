package com.fundpilot.backend.productcatalog.infrastructure.persistence.product;

import com.fundpilot.backend.productcatalog.domain.product.DefaultDisciplineCategory;
import com.fundpilot.backend.productcatalog.domain.product.InvestmentTarget;
import com.fundpilot.backend.productcatalog.domain.product.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "fund_product")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_date IS NULL")
@Getter
@Setter
class FundProductJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @CreatedDate @Column(updatable = false) private Instant createdDate;
    @LastModifiedDate private Instant updatedDate;
    @Column(name = "deleted_date", insertable = false, updatable = false) private Instant deletedDate;
    @Column(name = "fund_code", nullable = false, length = 16) private String fundCode;
    @Column(name = "fund_name", nullable = false, length = 255) private String fundName;
    @Column(name = "raw_name", length = 64) private String rawName;
    @Enumerated(EnumType.STRING) @Column(name = "product_type", length = 32) private ProductType productType;
    @Enumerated(EnumType.STRING) @Column(name = "investment_target", length = 32) private InvestmentTarget investmentTarget;
    @Column(name = "benchmark_index_code", length = 64) private String benchmarkIndexCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_discipline_category", length = 32)
    private DefaultDisciplineCategory defaultDisciplineCategory;
}
