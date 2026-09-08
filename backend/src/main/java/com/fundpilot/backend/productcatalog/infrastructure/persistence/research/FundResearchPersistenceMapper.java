package com.fundpilot.backend.productcatalog.infrastructure.persistence.research;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Industry;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Scale;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Snapshot;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import tools.jackson.databind.json.JsonMapper;

final class FundResearchPersistenceMapper {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private FundResearchPersistenceMapper() {}

    static FundResearch toDomain(FundResearchJpaEntity entity) {
        return FundResearch.rehydrate(entity.getId(), entity.getFundCode(),
                snapshot(entity.getProfileData(), Profile.class, entity.getProfileSourceName(),
                        entity.getProfileSourceUrl(), entity.getProfileReportDate(),
                        entity.getProfileFetchedAt(), entity.getProfileStatus()),
                snapshot(entity.getScaleData(), Scale.class, entity.getScaleSourceName(),
                        entity.getScaleSourceUrl(), entity.getScaleReportDate(),
                        entity.getScaleFetchedAt(), entity.getScaleStatus()),
                snapshot(entity.getHoldingsData(), Holdings.class, entity.getHoldingsSourceName(),
                        entity.getHoldingsSourceUrl(), entity.getHoldingsReportDate(),
                        entity.getHoldingsFetchedAt(), entity.getHoldingsStatus()),
                snapshot(entity.getIndustryData(), Industry.class, entity.getIndustrySourceName(),
                        entity.getIndustrySourceUrl(), entity.getIndustryReportDate(),
                        entity.getIndustryFetchedAt(), entity.getIndustryStatus()),
                entity.getLastAttemptAt());
    }

    static FundResearchJpaEntity toEntity(FundResearch research) {
        FundResearchJpaEntity entity = new FundResearchJpaEntity();
        entity.setFundCode(research.fundCode());
        copyMutable(research, entity);
        return entity;
    }

    static void copyMutable(FundResearch research, FundResearchJpaEntity entity) {
        writeProfile(research.profile(), entity);
        writeScale(research.scale(), entity);
        writeHoldings(research.holdings(), entity);
        writeIndustry(research.industry(), entity);
        entity.setLastAttemptAt(research.lastAttemptAt());
    }

    private static void writeProfile(Snapshot<Profile> value, FundResearchJpaEntity entity) {
        entity.setProfileData(json(value));
        entity.setProfileSourceName(sourceName(value));
        entity.setProfileSourceUrl(sourceUrl(value));
        entity.setProfileReportDate(reportDate(value));
        entity.setProfileFetchedAt(fetchedAt(value));
        entity.setProfileStatus(value == null ? null : value.status());
    }

    private static void writeScale(Snapshot<Scale> value, FundResearchJpaEntity entity) {
        entity.setScaleData(json(value));
        entity.setScaleSourceName(sourceName(value));
        entity.setScaleSourceUrl(sourceUrl(value));
        entity.setScaleReportDate(reportDate(value));
        entity.setScaleFetchedAt(fetchedAt(value));
        entity.setScaleStatus(value == null ? null : value.status());
    }

    private static void writeHoldings(Snapshot<Holdings> value, FundResearchJpaEntity entity) {
        entity.setHoldingsData(json(value));
        entity.setHoldingsSourceName(sourceName(value));
        entity.setHoldingsSourceUrl(sourceUrl(value));
        entity.setHoldingsReportDate(reportDate(value));
        entity.setHoldingsFetchedAt(fetchedAt(value));
        entity.setHoldingsStatus(value == null ? null : value.status());
    }

    private static void writeIndustry(Snapshot<Industry> value, FundResearchJpaEntity entity) {
        entity.setIndustryData(json(value));
        entity.setIndustrySourceName(sourceName(value));
        entity.setIndustrySourceUrl(sourceUrl(value));
        entity.setIndustryReportDate(reportDate(value));
        entity.setIndustryFetchedAt(fetchedAt(value));
        entity.setIndustryStatus(value == null ? null : value.status());
    }

    private static String json(Snapshot<?> value) {
        return value == null || value.data() == null ? null : JSON.writeValueAsString(value.data());
    }
    private static String sourceName(Snapshot<?> value) {
        return value == null || value.source() == null ? null : value.source().name();
    }
    private static String sourceUrl(Snapshot<?> value) {
        return value == null || value.source() == null ? null : value.source().url();
    }
    private static java.time.Instant reportDate(Snapshot<?> value) { return value == null ? null : value.reportDate(); }
    private static java.time.Instant fetchedAt(Snapshot<?> value) { return value == null ? null : value.fetchedAt(); }

    private static <T> Snapshot<T> snapshot(String json, Class<T> type, String sourceName,
                                            String sourceUrl, java.time.Instant reportDate,
                                            java.time.Instant fetchedAt, FundResearch.FetchStatus status) {
        if (status == null) return null;
        T data = json == null ? null : JSON.readValue(json, type);
        Source source = sourceName == null ? null : new Source(sourceName, sourceUrl);
        return new Snapshot<>(data, source, reportDate, fetchedAt, status);
    }
}
