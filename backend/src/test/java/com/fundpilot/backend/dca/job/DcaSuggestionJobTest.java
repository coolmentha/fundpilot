package com.fundpilot.backend.dca.job;

import com.fundpilot.backend.dca.controller.DcaPlanRequest;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.dca.service.DcaPlanService;
import com.fundpilot.backend.dca.service.DcaSuggestionService;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DcaSuggestionJob:周定投/月定投命中、月定投节假日顺延、幂等去重、enabled=false 跳过。
 * <p>用 {@link DcaSuggestionJob#generateForFund} 直测生成逻辑(绕开 run() 的 isTradingDay 早返与定时触发)。
 * 日期统一用 Asia/Shanghai 时区构造 Instant,与 Job 内部 TRADING_ZONE 对齐。
 */
class DcaSuggestionJobTest extends AbstractIntegrationTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    DcaSuggestionService dcaSuggestionService;

    @Autowired
    DcaPlanService dcaPlanService;

    @Autowired
    FundDcaPlanRepository fundDcaPlanRepository;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundTransactionRepository fundTransactionRepository;

    @Autowired
    TradingCalendarRepository tradingCalendarRepository;

    @Autowired
    TradingCalendarService tradingCalendarService;

    // ===== 周定投 =====

    @Test
    @Transactional
    void 周定投_命中定投日_生成_PENDING_INVEST() {
        FundEntity fund = persistFund();
        Long planId = activateWeekly(fund, 1); // 每周一
        // 2026-06-22 是周一
        Instant monday = date(2026, 6, 22);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), monday);

        assertThat(generated).isTrue();
        FundTransactionEntity tx = findPendingTx(fund.getId());
        assertThat(tx.getSource()).isEqualTo(FundTransactionSource.INVEST);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(tx.getDcaPlanId()).isEqualTo(planId);
    }

    @Test
    @Transactional
    void 周定投_非定投日_不生成() {
        FundEntity fund = persistFund();
        activateWeekly(fund, 1); // 每周一
        // 2026-06-23 是周二
        Instant tuesday = date(2026, 6, 23);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), tuesday);

        assertThat(generated).isFalse();
        assertThat(fundTransactionRepository.findByFundEntity_IdAndStatus(
                fund.getId(), FundTransactionStatus.PENDING)).isEmpty();
    }

    // ===== 月定投 =====

    @Test
    @Transactional
    void 月定投_命中定投日_生成() {
        FundEntity fund = persistFund();
        activateMonthly(fund, 15);
        Instant day15 = date(2026, 6, 15);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), day15);

        assertThat(generated).isTrue();
        assertThat(findPendingTx(fund.getId()).getDcaPlanId()).isNotNull();
    }

    @Test
    @Transactional
    void 月定投_计划日是交易日_已过不补() {
        FundEntity fund = persistFund();
        activateMonthly(fund, 15);
        // 06-15 是交易日,今天 06-16:计划日已过且是交易日,不补
        saveCalendar(LocalDate.of(2026, 6, 15), true);
        Instant day16 = date(2026, 6, 16);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), day16);

        assertThat(generated).isFalse();
    }

    @Test
    @Transactional
    void 月定投_计划日非交易日_顺延到下一交易日() {
        FundEntity fund = persistFund();
        activateMonthly(fund, 15);
        // 06-15 非交易日(节假日),06-16 是交易日:今天补执行
        saveCalendar(LocalDate.of(2026, 6, 15), false);
        Instant day16 = date(2026, 6, 16);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), day16);

        assertThat(generated).isTrue();
        assertThat(findPendingTx(fund.getId()).getSource()).isEqualTo(FundTransactionSource.INVEST);
    }

    @Test
    @Transactional
    void 月定投_月末连续休市_跨月顺延到下月首个交易日() {
        FundEntity fund = persistFund();
        activateMonthly(fund, 28);
        saveCalendar(LocalDate.of(2026, 6, 28), false);
        saveCalendar(LocalDate.of(2026, 6, 29), false);
        saveCalendar(LocalDate.of(2026, 6, 30), false);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), date(2026, 7, 1));

        assertThat(generated).isTrue();
        assertThat(findPendingTx(fund.getId()).getSource()).isEqualTo(FundTransactionSource.INVEST);
    }

    // ===== 幂等去重 =====

    @Test
    @Transactional
    void 同日同计划重跑_跳过_只生成一笔() {
        FundEntity fund = persistFund();
        // 用日定投 + 真实当前时间，使 createdDate 落在幂等窗口内且不受周末影响。
        activateDaily(fund);
        Instant now = Instant.now();

        boolean first = dcaSuggestionService.generateForFund(fund.getId(), now);
        boolean second = dcaSuggestionService.generateForFund(fund.getId(), now);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        List<FundTransactionEntity> pending = fundTransactionRepository.findByFundEntity_IdAndStatus(
                fund.getId(), FundTransactionStatus.PENDING);
        assertThat(pending).hasSize(1);
    }

    @Test
    @Transactional
    void 数据库唯一索引_同计划同北京时间自然日原子拒绝重复() {
        FundEntity fund = persistFund();
        Long planId = activateDaily(fund);
        Instant morning = Instant.parse("2026-07-08T01:00:00Z");
        Instant afternoon = Instant.parse("2026-07-08T07:00:00Z");

        int first = fundTransactionRepository.insertDcaPendingIfAbsent(
                fund.getId(), new BigDecimal("1000"), morning, planId);
        int second = fundTransactionRepository.insertDcaPendingIfAbsent(
                fund.getId(), new BigDecimal("1000"), afternoon, planId);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    @Transactional
    void 同日交易已确认_重跑也不重复生成() {
        FundEntity fund = persistFund();
        activateDaily(fund);
        Instant now = Instant.now();
        assertThat(dcaSuggestionService.generateForFund(fund.getId(), now)).isTrue();
        FundTransactionEntity tx = findPendingTx(fund.getId());
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        fundTransactionRepository.saveAndFlush(tx);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), now);

        assertThat(generated).isFalse();
        assertThat(fundTransactionRepository.findByFundEntity_Id(fund.getId())).hasSize(1);
    }

    @Test
    @Transactional
    void 同日交易已撤销_重跑也不重复生成() {
        FundEntity fund = persistFund();
        activateDaily(fund);
        Instant now = Instant.now();
        assertThat(dcaSuggestionService.generateForFund(fund.getId(), now)).isTrue();
        FundTransactionEntity tx = findPendingTx(fund.getId());
        tx.setStatus(FundTransactionStatus.CANCELLED);
        fundTransactionRepository.saveAndFlush(tx);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), now);

        assertThat(generated).isFalse();
        assertThat(fundTransactionRepository.findByFundEntity_Id(fund.getId())).hasSize(1);
    }

    // ===== enabled=false 跳过 =====

    @Test
    @Transactional
    void enabled_false_即使命中定投日也跳过() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(),
                new DcaPlanRequest(false, new BigDecimal("1000"), DcaFrequency.WEEKLY, 1, null));
        Instant monday = date(2026, 6, 22);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), monday);

        assertThat(generated).isFalse();
        assertThat(fundTransactionRepository.findByFundEntity_IdAndStatus(
                fund.getId(), FundTransactionStatus.PENDING)).isEmpty();
    }

    // ===== 日定投 =====

    @Test
    @Transactional
    void 日定投_每个交易日都生成() {
        FundEntity fund = persistFund();
        activateDaily(fund);
        // 2026-06-23 周二,任意交易日(日定投不看星期)
        Instant tuesday = date(2026, 6, 23);

        boolean generated = dcaSuggestionService.generateForFund(fund.getId(), tuesday);

        assertThat(generated).isTrue();
        assertThat(findPendingTx(fund.getId()).getSource()).isEqualTo(FundTransactionSource.INVEST);
    }

    // ===== 辅助 =====

    private Long activateDaily(FundEntity fund) {
        return dcaPlanService.create(fund.getId(),
                new DcaPlanRequest(true, new BigDecimal("1000"), DcaFrequency.DAILY, null, null));
    }

    private Long activateWeekly(FundEntity fund, int dayOfWeek) {
        return dcaPlanService.create(fund.getId(),
                new DcaPlanRequest(true, new BigDecimal("1000"), DcaFrequency.WEEKLY, dayOfWeek, null));
    }

    private Long activateMonthly(FundEntity fund, int dayOfMonth) {
        return dcaPlanService.create(fund.getId(),
                new DcaPlanRequest(true, new BigDecimal("1000"), DcaFrequency.MONTHLY, null, dayOfMonth));
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("161725");
        fund.setFundName("测试基金");
        return fundRepository.save(fund);
    }

    /** 构造某日 Asia/Shanghai 0 点的 Instant(与 Job 内部 TRADING_ZONE 对齐)。 */
    private static Instant date(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(SHANGHAI).toInstant();
    }

    private void saveCalendar(LocalDate date, boolean tradingDay) {
        TradingCalendarEntity entity = new TradingCalendarEntity();
        entity.setCalendarDate(date.atStartOfDay(ZoneOffset.UTC).toInstant());
        entity.setTradingDay(tradingDay);
        tradingCalendarRepository.save(entity);
    }

    private FundTransactionEntity findPendingTx(Long fundId) {
        List<FundTransactionEntity> txs = fundTransactionRepository.findByFundEntity_IdAndStatus(
                fundId, FundTransactionStatus.PENDING);
        assertThat(txs).hasSize(1);
        return txs.get(0);
    }
}
