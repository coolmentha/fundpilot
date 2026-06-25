package com.fundpilot.backend.fund.service.support;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #8 循环 A:{@code BenchmarkIndexTable} 指数关键词 → secid 映射。
 * 覆盖 issue 要求的 6 个核心指数;未命中返回 empty。
 */
class BenchmarkIndexTableTest {

    @Test
    void 沪深300_关键词命中_000300_SH() {
        assertThat(BenchmarkIndexTable.lookup("沪深300ETF联接")).contains("000300.SH");
        assertThat(BenchmarkIndexTable.lookup("沪深300指数增强")).contains("000300.SH");
    }

    @Test
    void 中证500_关键词命中_000905_SH() {
        assertThat(BenchmarkIndexTable.lookup("南方中证500ETF")).contains("000905.SH");
    }

    @Test
    void 上证50_关键词命中_000016_SH() {
        assertThat(BenchmarkIndexTable.lookup("易方达上证50指数")).contains("000016.SH");
    }

    @Test
    void 创业板_关键词命中_399006_SZ() {
        assertThat(BenchmarkIndexTable.lookup("创业板ETF")).contains("399006.SZ");
    }

    @Test
    void 科创50_关键词命中_000688_SH() {
        assertThat(BenchmarkIndexTable.lookup("华夏科创50ETF")).contains("000688.SH");
    }

    @Test
    void 中证1000_关键词命中_000852_SH() {
        assertThat(BenchmarkIndexTable.lookup("中证1000指数")).contains("000852.SH");
    }

    @Test
    void 未命中关键词_返回_empty() {
        assertThat(BenchmarkIndexTable.lookup("中欧医疗健康混合A")).isEmpty();
        assertThat(BenchmarkIndexTable.lookup("兴全合宜混合")).isEmpty();
    }

    @Test
    void null_或空字符串_返回_empty() {
        assertThat(BenchmarkIndexTable.lookup(null)).isEmpty();
        assertThat(BenchmarkIndexTable.lookup("")).isEmpty();
        assertThat(BenchmarkIndexTable.lookup("   ")).isEmpty();
    }
}
