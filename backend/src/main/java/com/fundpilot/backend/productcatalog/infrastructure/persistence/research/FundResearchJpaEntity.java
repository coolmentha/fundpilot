package com.fundpilot.backend.productcatalog.infrastructure.persistence.research;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch.FetchStatus;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "fund_research")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
class FundResearchJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @CreatedDate @Column(updatable = false) private Instant createdDate;
    @LastModifiedDate private Instant updatedDate;
    @Column(name = "fund_code", nullable = false, length = 16) private String fundCode;
    @Column(name = "profile_data", columnDefinition = "TEXT") private String profileData;
    @Column(name = "profile_source_name") private String profileSourceName;
    @Column(name = "profile_source_url", length = 1000) private String profileSourceUrl;
    @Column(name = "profile_report_date") private Instant profileReportDate;
    @Column(name = "profile_fetched_at") private Instant profileFetchedAt;
    @Enumerated(EnumType.STRING) @Column(name = "profile_status", length = 16) private FetchStatus profileStatus;

    @Column(name = "scale_data", columnDefinition = "TEXT") private String scaleData;
    @Column(name = "scale_source_name") private String scaleSourceName;
    @Column(name = "scale_source_url", length = 1000) private String scaleSourceUrl;
    @Column(name = "scale_report_date") private Instant scaleReportDate;
    @Column(name = "scale_fetched_at") private Instant scaleFetchedAt;
    @Enumerated(EnumType.STRING) @Column(name = "scale_status", length = 16) private FetchStatus scaleStatus;

    @Column(name = "holdings_data", columnDefinition = "TEXT") private String holdingsData;
    @Column(name = "holdings_source_name") private String holdingsSourceName;
    @Column(name = "holdings_source_url", length = 1000) private String holdingsSourceUrl;
    @Column(name = "holdings_report_date") private Instant holdingsReportDate;
    @Column(name = "holdings_fetched_at") private Instant holdingsFetchedAt;
    @Enumerated(EnumType.STRING) @Column(name = "holdings_status", length = 16) private FetchStatus holdingsStatus;

    @Column(name = "industry_data", columnDefinition = "TEXT") private String industryData;
    @Column(name = "industry_source_name") private String industrySourceName;
    @Column(name = "industry_source_url", length = 1000) private String industrySourceUrl;
    @Column(name = "industry_report_date") private Instant industryReportDate;
    @Column(name = "industry_fetched_at") private Instant industryFetchedAt;
    @Enumerated(EnumType.STRING) @Column(name = "industry_status", length = 16) private FetchStatus industryStatus;
    @Column(name = "last_attempt_at") private Instant lastAttemptAt;
}
