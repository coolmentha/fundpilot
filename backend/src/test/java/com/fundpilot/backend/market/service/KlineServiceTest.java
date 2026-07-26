package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.controller.KlineView;
import com.fundpilot.backend.market.entity.IndexKlineEntity;
import com.fundpilot.backend.market.repository.IndexKlineRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KlineService 缓存读取 + 日/周/月 K 聚合。
 * <p>验证:有 index_kline 缓存时返回 chartType=kline,不调 push2his;周/月 K 由日 K 正确聚合
 * (open=首日、high=max、low=min、close=末日、volume=sum,date=周期末日)。
 */
class KlineServiceTest extends AbstractIntegrationTest {

    @Autowired
    KlineService klineService;
    @Autowired
    FundRepository fundRepository;
    @Autowired
    IndexKlineRepository indexKlineRepository;

    @Test
    @Transactional
    void 日K_缓存原样返回() {
        FundEntity fund = persistIndexFund();
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-22", "100", "105", "99", "103", 1000L);
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-23", "103", "106", "102", "104", 1200L);

        KlineView view = klineService.getKline(fund.getId(), "daily");

        assertThat(view.chartType()).isEqualTo("kline");
        assertThat(view.bars()).hasSize(2);
        assertThat(view.bars().get(0).close()).isEqualByComparingTo("103");
    }

    @Test
    @Transactional
    void 周K_按周聚合_open首high最大low最小close末volumeSum() {
        FundEntity fund = persistIndexFund();
        // 06-22(周一)/23(周二)/24(周三) 同一周;06-29(下周一) 次周
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-22", "100", "105", "99", "103", 1000L);
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-23", "103", "106", "102", "104", 1200L);
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-24", "104", "107", "103", "106", 900L);
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-29", "106", "108", "105", "107", 800L);

        KlineView view = klineService.getKline(fund.getId(), "weekly");

        assertThat(view.bars()).hasSize(2);
        KlineView.Bar week1 = view.bars().get(0);
        assertThat(week1.open()).isEqualByComparingTo("100");   // 首日 open
        assertThat(week1.high()).isEqualByComparingTo("107");   // max
        assertThat(week1.low()).isEqualByComparingTo("99");     // min
        assertThat(week1.close()).isEqualByComparingTo("106");  // 末日 close
        assertThat(week1.volume()).isEqualTo(3100L);            // 1000+1200+900
        // 周期末日:06-24
        assertThat(view.bars().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 24).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    @Transactional
    void 月K_按月聚合() {
        FundEntity fund = persistIndexFund();
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-22", "100", "105", "99", "103", 1000L);
        saveBar(fund.getBenchmarkIndexCode(), "2026-06-24", "104", "107", "103", "106", 900L);
        saveBar(fund.getBenchmarkIndexCode(), "2026-07-01", "106", "108", "105", "107", 800L);

        KlineView view = klineService.getKline(fund.getId(), "monthly");

        assertThat(view.bars()).hasSize(2); // 6月、7月各一根
        KlineView.Bar june = view.bars().get(0);
        assertThat(june.open()).isEqualByComparingTo("100");
        assertThat(june.close()).isEqualByComparingTo("106");
        assertThat(june.volume()).isEqualTo(1900L);
    }

    @Test
    @Transactional
    void 无缓存且无基准_降级净值走势() {
        FundEntity fund = new FundEntity();
        fund.setOwnerId(testActorId());
        fund.setFundCode("000001");
        fund.setFundName("主动基金");
        fund.setFundSubType(FundSubType.ACTIVE); // 非指数型
        fundRepository.save(fund);

        KlineView view = klineService.getKline(fund.getId(), "daily");

        assertThat(view.chartType()).isEqualTo("nav");
    }

    private FundEntity persistIndexFund() {
        FundEntity fund = new FundEntity();
        fund.setOwnerId(testActorId());
        fund.setFundCode("161725");
        fund.setFundName("测试指数基金");
        fund.setFundSubType(FundSubType.INDEX);
        fund.setBenchmarkIndexCode("930713.CSI");
        return fundRepository.save(fund);
    }

    private void saveBar(String indexCode, String date, String open, String high, String low, String close, long vol) {
        IndexKlineEntity e = new IndexKlineEntity();
        e.setIndexCode(indexCode);
        e.setTradeDate(LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant());
        e.setOpen(new BigDecimal(open));
        e.setHigh(new BigDecimal(high));
        e.setLow(new BigDecimal(low));
        e.setClose(new BigDecimal(close));
        e.setVolume(vol);
        indexKlineRepository.save(e);
    }
}
