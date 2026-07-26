package com.fundpilot.backend.productcatalog.domain.product;

public record ProductClassification(ProductType productType,
                                    DefaultDisciplineCategory defaultDisciplineCategory,
                                    String benchmarkIndexCode) {
}
