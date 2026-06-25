package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.EastmoneyClient;
import com.fundpilot.backend.market.client.FundDictEntry;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * issue #8 循环 D:{@code FundDictBackfillService.backfillAll} 从东方财富字典批量识别
 * 已有 fund 表 {@code fund_sub_type IS NULL} 的行,填回 fundSubType + benchmarkIndexCode。
 */
class FundDictBackfillServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    EastmoneyClient eastmoneyClient;

    @Autowired
    FundDictBackfillService fundDictBackfillService;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void backfillAll_字典匹配后填回_fundSubType_和_benchmarkIndexCode() {
        FundEntity etf = persistNullSubTypeFund("510310", "易方达沪深300ETF");
        FundEntity active = persistNullSubTypeFund("163406", "兴全合宜混合A");

        when(eastmoneyClient.fetchFundDict()).thenReturn(List.of(
                new FundDictEntry("510310", "易方达沪深300ETF", "ETF"),
                new FundDictEntry("163406", "兴全合宜混合A", "混合型")
        ));

        int updated = fundDictBackfillService.backfillAll();

        assertThat(updated).isEqualTo(2);
        FundEntity reloadedEtf = fundRepository.findById(etf.getId()).orElseThrow();
        assertThat(reloadedEtf.getFundSubType()).isEqualTo(FundSubType.ETF);
        assertThat(reloadedEtf.getBenchmarkIndexCode()).isEqualTo("000300.SH");
        FundEntity reloadedActive = fundRepository.findById(active.getId()).orElseThrow();
        assertThat(reloadedActive.getFundSubType()).isEqualTo(FundSubType.ACTIVE);
        assertThat(reloadedActive.getBenchmarkIndexCode()).isEqualTo("000300.SH");
    }

    @Test
    @Transactional
    void backfillAll_已有_fundSubType_的行不覆盖() {
        FundEntity alreadyClassified = persistNullSubTypeFund("161725", "招商中证白酒指数");
        alreadyClassified.setFundSubType(FundSubType.INDEX);
        alreadyClassified.setBenchmarkIndexCode("000300.SH");
        fundRepository.save(alreadyClassified);

        when(eastmoneyClient.fetchFundDict()).thenReturn(List.of(
                new FundDictEntry("161725", "招商中证白酒指数LOF", "指数型")
        ));

        int updated = fundDictBackfillService.backfillAll();

        assertThat(updated).isEqualTo(0);
    }

    @Test
    @Transactional
    void backfillAll_字典无匹配_fundCode_跳过不报错() {
        persistNullSubTypeFund("999999", "未知基金");

        when(eastmoneyClient.fetchFundDict()).thenReturn(List.of());

        int updated = fundDictBackfillService.backfillAll();

        assertThat(updated).isEqualTo(0);
    }

    private FundEntity persistNullSubTypeFund(String code, String name) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setFundName(name);
        // fundSubType 留 null,模拟待 backfill
        return fundRepository.save(fund);
    }
}
