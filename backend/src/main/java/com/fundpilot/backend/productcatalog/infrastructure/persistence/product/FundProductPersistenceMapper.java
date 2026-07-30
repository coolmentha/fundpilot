package com.fundpilot.backend.productcatalog.infrastructure.persistence.product;

import com.fundpilot.backend.productcatalog.domain.product.FundProduct;

final class FundProductPersistenceMapper {
    private FundProductPersistenceMapper() {}

    static FundProduct toDomain(FundProductJpaEntity entity) {
        return FundProduct.rehydrate(entity.getId(), entity.getFundCode(), entity.getFundName(),
                entity.getRawName(), entity.getProductType(), entity.getInvestmentTarget(),
                entity.getBenchmarkIndexCode(), entity.getDefaultDisciplineCategory());
    }

    static FundProductJpaEntity toEntity(FundProduct product) {
        FundProductJpaEntity entity = new FundProductJpaEntity();
        entity.setFundCode(product.fundCode());
        copyMutable(product, entity);
        return entity;
    }

    static void copyMutable(FundProduct product, FundProductJpaEntity entity) {
        entity.setFundName(product.fundName());
        entity.setRawName(product.rawName());
        entity.setProductType(product.productType());
        entity.setInvestmentTarget(product.investmentTarget());
        entity.setBenchmarkIndexCode(product.benchmarkIndexCode());
        entity.setDefaultDisciplineCategory(product.defaultDisciplineCategory());
    }
}
