package com.fundpilot.backend.productcatalog.infrastructure.persistence.fee;

import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule;
import com.fundpilot.backend.productcatalog.domain.fee.RedemptionFeeTier;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class FundFeeSchedulePersistenceMapper {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FundFeeSchedulePersistenceMapper() {}

    static FundFeeSchedule toDomain(FundFeeScheduleJpaEntity entity) {
        return FundFeeSchedule.rehydrate(entity.getId(), entity.getFundCode(), entity.getPurchaseRate(),
                entity.getDiscountRate(), entity.getSalesServiceFee(), parseTiers(entity.getRedemptionLadder()),
                entity.getManagementFee(), entity.getCustodyFee(), entity.getPurchaseStatus(),
                entity.getPurchaseLimit(), entity.getMinimumPurchaseAmount(), entity.getChannelReferenceLabel(),
                entity.getSourceName(), entity.getSourceUrl(), entity.getRefreshStatus(), entity.getFetchedAt());
    }

    static FundFeeScheduleJpaEntity toEntity(FundFeeSchedule schedule) {
        FundFeeScheduleJpaEntity entity = new FundFeeScheduleJpaEntity();
        entity.setFundCode(schedule.fundCode());
        copyMutable(schedule, entity);
        return entity;
    }

    static void copyMutable(FundFeeSchedule schedule, FundFeeScheduleJpaEntity entity) {
        entity.setPurchaseRate(schedule.purchaseRate());
        entity.setDiscountRate(schedule.discountRate());
        entity.setSalesServiceFee(schedule.salesServiceFee());
        entity.setManagementFee(schedule.managementFee());
        entity.setCustodyFee(schedule.custodyFee());
        entity.setPurchaseStatus(schedule.purchaseStatus());
        entity.setPurchaseLimit(schedule.purchaseLimit());
        entity.setMinimumPurchaseAmount(schedule.minimumPurchaseAmount());
        entity.setChannelReferenceLabel(schedule.channelReferenceLabel());
        entity.setSourceName(schedule.sourceName());
        entity.setSourceUrl(schedule.sourceUrl());
        entity.setRefreshStatus(schedule.refreshStatus());
        entity.setRedemptionLadder(schedule.redemptionTiers().isEmpty()
                ? null : JSON.writeValueAsString(schedule.redemptionTiers()));
        entity.setFetchedAt(schedule.fetchedAt());
    }

    private static List<RedemptionFeeTier> parseTiers(String value) {
        if (value == null || value.isBlank()) return List.of();
        return JSON.readValue(value, new TypeReference<>() {});
    }
}
