package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.controller.DcaBudgetSummaryView;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.entity.UserConfigEntity;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import com.fundpilot.backend.user.service.UserConfigService;
import com.fundpilot.backend.user.service.CurrentUserService;
import com.fundpilot.backend.fund.service.FundAccessService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DcaBudgetSummaryServiceTest extends AbstractIntegrationTest {

    private static final Instant JULY_13_BEFORE_EXECUTION = Instant.parse("2026-07-13T06:54:00Z");

    @Autowired
    DcaScheduleService dcaScheduleService;

    @Autowired
    UserConfigService userConfigService;

    @Autowired
    CurrentUserService currentUserService;

    @Autowired
    FundAccessService fundAccessService;

    @Autowired
    UserConfigRepository userConfigRepository;

    @Autowired
    FundDcaPlanRepository fundDcaPlanRepository;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundTransactionRepository fundTransactionRepository;

    @Autowired
    TradingCalendarRepository tradingCalendarRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void currentMonth_统计手动和自动的待确认已确认定投_取消交易不计入且已生成日期不再预测() {
        saveBudget("1000");
        FundDcaPlanEntity plan = savePlan(DcaFrequency.DAILY, "300", null);
        saveCalendar("2026-07-13T00:00:00Z", true);
        saveInvest(FundTransactionStatus.PENDING, "200", "2026-07-01T02:00:00Z", null);
        saveInvest(FundTransactionStatus.CONFIRMED, "300", "2026-07-02T02:00:00Z", plan.getId());
        saveInvest(FundTransactionStatus.CANCELLED, "400", "2026-07-03T02:00:00Z", null);
        saveInvest(FundTransactionStatus.PENDING, "300", "2026-07-13T06:55:00Z", plan.getId());
        entityManager.flush();

        DcaBudgetSummaryView view = summaryAt(JULY_13_BEFORE_EXECUTION).currentMonth();

        assertThat(view.investedAmount()).isEqualByComparingTo("800");
        assertThat(view.futureAmount()).isEqualByComparingTo("0");
        assertThat(view.projectedAmount()).isEqualByComparingTo("800");
        assertThat(view.remainingAmount()).isEqualByComparingTo("200");
        assertThat(view.overBudgetAmount()).isEqualByComparingTo("0");
    }

    @Test
    void currentMonth_当天14点55分前预测计划_到点后不再计入未来() {
        savePlan(DcaFrequency.DAILY, "120", null);
        saveCalendar("2026-07-13T00:00:00Z", true);
        entityManager.flush();

        DcaBudgetSummaryView before = summaryAt(JULY_13_BEFORE_EXECUTION).currentMonth();
        DcaBudgetSummaryView atExecutionTime = summaryAt(Instant.parse("2026-07-13T06:55:00Z")).currentMonth();

        assertThat(before.futureAmount()).isEqualByComparingTo("120");
        assertThat(atExecutionTime.futureAmount()).isEqualByComparingTo("0");
    }

    @Test
    void currentMonth_月计划月末连续休市后按下月实际执行日归属() {
        savePlan(DcaFrequency.MONTHLY, "500", 28);
        saveCalendar("2026-06-28T00:00:00Z", false);
        saveCalendar("2026-06-29T00:00:00Z", false);
        saveCalendar("2026-06-30T00:00:00Z", false);
        saveCalendar("2026-07-01T00:00:00Z", true);
        entityManager.flush();

        DcaBudgetSummaryView view = summaryAt(Instant.parse("2026-07-01T06:00:00Z")).currentMonth();

        assertThat(view.monthlyBudget()).isNull();
        assertThat(view.investedAmount()).isEqualByComparingTo("0");
        assertThat(view.futureAmount()).isEqualByComparingTo("500");
        assertThat(view.remainingAmount()).isNull();
        assertThat(view.overBudgetAmount()).isNull();
    }

    @Test
    void currentMonth_预计金额超过预算时返回超额而非拦截信息() {
        saveBudget("500");
        saveInvest(FundTransactionStatus.CONFIRMED, "650", "2026-07-02T02:00:00Z", null);
        entityManager.flush();

        DcaBudgetSummaryView view = summaryAt(JULY_13_BEFORE_EXECUTION).currentMonth();

        assertThat(view.projectedAmount()).isEqualByComparingTo("650");
        assertThat(view.remainingAmount()).isEqualByComparingTo("0");
        assertThat(view.overBudgetAmount()).isEqualByComparingTo("150");
    }

    private DcaBudgetSummaryService summaryAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DcaPlanForecastService forecastService = new DcaPlanForecastService(
                fundDcaPlanRepository, fundTransactionRepository, fundAccessService, dcaScheduleService, clock);
        return new DcaBudgetSummaryService(
                userConfigService,
                fundTransactionRepository,
                dcaScheduleService,
                forecastService,
                currentUserService,
                clock);
    }

    private void saveBudget(String value) {
        UserConfigEntity config = userConfigRepository.findAll().stream()
                .findFirst().orElseGet(UserConfigEntity::new);
        config.setMonthlyDcaBudget(new BigDecimal(value));
        userConfigRepository.save(config);
    }

    private FundDcaPlanEntity savePlan(DcaFrequency frequency, String amount, Integer dayOfMonth) {
        FundEntity fund = new FundEntity();
        fund.setFundCode("budget-" + fundRepository.count());
        fund.setFundName("预算测试基金");
        fundRepository.save(fund);

        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setFundEntity(fund);
        plan.setEnabled(true);
        plan.setAmount(new BigDecimal(amount));
        plan.setFrequency(frequency);
        plan.setDayOfMonth(dayOfMonth);
        plan.setStatus(DcaPlanStatus.EFFECTIVE);
        return fundDcaPlanRepository.save(plan);
    }

    private void saveInvest(FundTransactionStatus status, String amount, String tradeDate, Long dcaPlanId) {
        FundEntity fund = dcaPlanId == null
                ? saveTransactionFund()
                : fundDcaPlanRepository.findById(dcaPlanId).orElseThrow().getFundEntity();

        FundTransactionEntity transaction = new FundTransactionEntity();
        transaction.setFundEntity(fund);
        transaction.setSource(FundTransactionSource.INVEST);
        transaction.setStatus(status);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTradeDate(Instant.parse(tradeDate));
        transaction.setDcaPlanId(dcaPlanId);
        fundTransactionRepository.save(transaction);
    }

    private FundEntity saveTransactionFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("transaction-" + fundRepository.count());
        fund.setFundName("定投交易测试基金");
        return fundRepository.save(fund);
    }

    private void saveCalendar(String calendarDate, boolean tradingDay) {
        TradingCalendarEntity calendar = new TradingCalendarEntity();
        calendar.setCalendarDate(Instant.parse(calendarDate));
        calendar.setTradingDay(tradingDay);
        tradingCalendarRepository.save(calendar);
    }
}
