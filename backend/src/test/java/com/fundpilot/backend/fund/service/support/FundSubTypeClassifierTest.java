package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundSubType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #8 循环 B:{@code FundSubTypeClassifier.classify} 按名称启发式识别
 * fundSubType + benchmarkIndexCode。严格对齐 issue 验收清单 7 个 case。
 * <p>识别优先级:ETF &gt; INDEX_ENHANCED &gt; INDEX &gt; ACTIVE(兜底)。
 */
class FundSubTypeClassifierTest {

    @Test
    void 易方达沪深300ETF_识别为_ETF_指数_000300() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("易方达沪深300ETF");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ETF);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 南方中证500ETF_识别为_ETF_指数_000905() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("南方中证500ETF");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ETF);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000905.SH");
    }

    @Test
    void 汇添富中证主要消费ETF_识别为_ETF_指数留空() {
        // 行业指数(中证主要消费)不在 6 个核心指数表内,benchmarkIndexCode 留 null
        FundSubTypeResult r = FundSubTypeClassifier.classify("汇添富中证主要消费ETF");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ETF);
        assertThat(r.benchmarkIndexCode()).isNull();
    }

    @Test
    void 易方达上证50指数增强A_识别为_INDEX_ENHANCED_指数_000016() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("易方达上证50指数增强A");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.INDEX_ENHANCED);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000016.SH");
    }

    @Test
    void 华夏沪深300指数A_识别为_INDEX_指数_000300() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("华夏沪深300指数A");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.INDEX);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 兴全合宜混合LOF_A_识别为_ACTIVE_默认指数_000300() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("兴全合宜混合(LOF)A");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ACTIVE);
        // ACTIVE 默认填沪深300(CONTEXT.md 明确,逻辑止损不使用)
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 中欧医疗健康混合A_识别为_ACTIVE_默认指数_000300() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("中欧医疗健康混合A");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ACTIVE);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 名称含指数关键词但无ETF无增强_识别为_INDEX() {
        // 「创业板」是指数关键词但不含 ETF/增强 → INDEX
        FundSubTypeResult r = FundSubTypeClassifier.classify("华夏创业板指数A");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.INDEX);
        assertThat(r.benchmarkIndexCode()).isEqualTo("399006.SZ");
    }

    // ===== 循环 C:边界用例 =====

    @Test
    void null_名称_兜底_ACTIVE_默认基准() {
        FundSubTypeResult r = FundSubTypeClassifier.classify(null);
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ACTIVE);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 空字符串_兜底_ACTIVE_默认基准() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ACTIVE);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 纯空白_兜底_ACTIVE_默认基准() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("   ");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ACTIVE);
    }

    @Test
    void 纯英文不含中文关键词_兜底_ACTIVE() {
        FundSubTypeResult r = FundSubTypeClassifier.classify("AB Balance Fund");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ACTIVE);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 含增强但无指数关键词_识别为_INDEX_ENHANCED_指数留空() {
        // 「XX增强」但 BenchmarkIndexTable 不认识 → INDEX_ENHANCED + null
        FundSubTypeResult r = FundSubTypeClassifier.classify("某某主动增强A");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.INDEX_ENHANCED);
        assertThat(r.benchmarkIndexCode()).isNull();
    }

    @Test
    void ETF联接_识别为_ETF() {
        // 「ETF联接」含 ETF 字样 → ETF
        FundSubTypeResult r = FundSubTypeClassifier.classify("易方达沪深300ETF联接A");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.ETF);
        assertThat(r.benchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    void 小写etf_不命中ETF规则_按指数关键词判INDEX() {
        // 关键词匹配大小写敏感——「沪深300etf」(小写)不含大写 ETF,
        // 但命中「沪深300」指数关键词 → INDEX(符合「命中指数关键词即 INDEX」规则)
        FundSubTypeResult r = FundSubTypeClassifier.classify("沪深300etf");
        assertThat(r.fundSubType()).isEqualTo(FundSubType.INDEX);
    }
}
