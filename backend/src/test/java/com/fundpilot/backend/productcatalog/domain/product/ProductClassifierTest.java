package com.fundpilot.backend.productcatalog.domain.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductClassifierTest {
    @Test
    void classifiesTrackedIndexProductAndSuggestsBroadBaseDiscipline() {
        var result = ProductClassifier.classify("易方达沪深300ETF");

        assertThat(result.productType()).isEqualTo(ProductType.ETF);
        assertThat(result.benchmarkIndexCode()).isEqualTo("000300.SH");
        assertThat(result.defaultDisciplineCategory()).isEqualTo(DefaultDisciplineCategory.BROAD_BASE);
    }

    @Test
    void classifiesActiveMixedProductWithoutTurningSuggestionIntoProductType() {
        var result = ProductClassifier.classify("兴全合宜混合A");

        assertThat(result.productType()).isEqualTo(ProductType.ACTIVE);
        assertThat(result.defaultDisciplineCategory()).isEqualTo(DefaultDisciplineCategory.MIXED);
        assertThat(result.benchmarkIndexCode()).isEqualTo(ProductClassifier.ACTIVE_DEFAULT_BENCHMARK);
    }

    @Test
    void sectorIndexProductSuggestsSectorDiscipline() {
        var result = ProductClassifier.classify("华夏国证半导体芯片ETF");

        assertThat(result.productType()).isEqualTo(ProductType.ETF);
        assertThat(result.benchmarkIndexCode()).isEqualTo("931865.CSI");
        assertThat(result.defaultDisciplineCategory()).isEqualTo(DefaultDisciplineCategory.SECTOR);
    }

    @Test
    void sectorIndexProductWithoutBenchmarkKeywordSuggestsSectorDiscipline() {
        var result = ProductClassifier.classify("汇添富中证主要消费ETF");

        assertThat(result.productType()).isEqualTo(ProductType.ETF);
        assertThat(result.defaultDisciplineCategory()).isEqualTo(DefaultDisciplineCategory.SECTOR);
    }

    @Test
    void investmentTargetCanOnlyBeIdentifiedOnce() {
        FundProduct product = FundProduct.create("019736", "QDII基金", null,
                ProductType.ACTIVE, null, null, DefaultDisciplineCategory.ACTIVE);

        product.identifyInvestmentTarget(InvestmentTarget.QDII);

        assertThat(product.investmentTarget()).isEqualTo(InvestmentTarget.QDII);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> product.identifyInvestmentTarget(InvestmentTarget.STOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("投资标的冲突");
    }
}
