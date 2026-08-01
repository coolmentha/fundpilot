package com.fundpilot.backend.productcatalog.domain.product;

import java.util.Objects;

public final class FundProduct {

    private final Long id;
    private final String fundCode;
    private String fundName;
    private String rawName;
    private ProductType productType;
    private InvestmentTarget investmentTarget;
    private String benchmarkIndexCode;
    private boolean benchmarkCustomized;
    private DefaultDisciplineCategory defaultDisciplineCategory;

    private FundProduct(Long id, String fundCode, String fundName, String rawName,
                        ProductType productType, InvestmentTarget investmentTarget,
                        String benchmarkIndexCode, boolean benchmarkCustomized,
                        DefaultDisciplineCategory defaultDisciplineCategory) {
        this.id = id;
        this.fundCode = requireText(fundCode, "基金代码");
        this.fundName = requireText(fundName, "基金名称");
        this.rawName = normalize(rawName);
        this.productType = productType;
        this.investmentTarget = investmentTarget;
        this.benchmarkIndexCode = normalize(benchmarkIndexCode);
        this.benchmarkCustomized = benchmarkCustomized;
        this.defaultDisciplineCategory = defaultDisciplineCategory;
    }

    public static FundProduct create(String fundCode, String fundName, String rawName,
                                     ProductType productType, InvestmentTarget investmentTarget,
                                     String benchmarkIndexCode,
                                     DefaultDisciplineCategory defaultDisciplineCategory) {
        return new FundProduct(null, fundCode, fundName, rawName, productType, investmentTarget,
                benchmarkIndexCode, false, defaultDisciplineCategory);
    }

    public static FundProduct rehydrate(Long id, String fundCode, String fundName, String rawName,
                                        ProductType productType, InvestmentTarget investmentTarget,
                                        String benchmarkIndexCode, boolean benchmarkCustomized,
                                        DefaultDisciplineCategory defaultDisciplineCategory) {
        return new FundProduct(Objects.requireNonNull(id), fundCode, fundName, rawName, productType,
                investmentTarget, benchmarkIndexCode, benchmarkCustomized, defaultDisciplineCategory);
    }

    public void refreshCatalogFacts(String fundName, String rawName, ProductType productType,
                                    String benchmarkIndexCode,
                                    DefaultDisciplineCategory defaultDisciplineCategory) {
        this.fundName = requireText(fundName, "基金名称");
        this.rawName = normalize(rawName);
        this.productType = productType;
        if (!benchmarkCustomized) {
            this.benchmarkIndexCode = normalize(benchmarkIndexCode);
        }
        this.defaultDisciplineCategory = defaultDisciplineCategory;
    }

    public void identifyInvestmentTarget(InvestmentTarget identifiedTarget) {
        if (identifiedTarget == null || identifiedTarget == investmentTarget) return;
        if (investmentTarget != null) {
            throw new IllegalStateException("基金投资标的冲突: " + investmentTarget + " -> " + identifiedTarget);
        }
        investmentTarget = identifiedTarget;
    }

    /** 用户手动补填/修正业绩比较基准(issue #146),同步到产品以统一建仓与批刷新口径；置标记防止目录同步回退。 */
    public void updateBenchmarkIndexCode(String benchmarkIndexCode) {
        this.benchmarkIndexCode = normalize(benchmarkIndexCode);
        this.benchmarkCustomized = true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long id() { return id; }
    public String fundCode() { return fundCode; }
    public String fundName() { return fundName; }
    public String rawName() { return rawName; }
    public ProductType productType() { return productType; }
    public InvestmentTarget investmentTarget() { return investmentTarget; }
    public String benchmarkIndexCode() { return benchmarkIndexCode; }
    public boolean benchmarkCustomized() { return benchmarkCustomized; }
    public DefaultDisciplineCategory defaultDisciplineCategory() { return defaultDisciplineCategory; }
}
