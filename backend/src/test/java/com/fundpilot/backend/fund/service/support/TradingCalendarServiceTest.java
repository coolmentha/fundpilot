package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #5 验收:TradingCalendarService.isTradingDay + daysBetweenTradingDays。
 * <p>{@code daysBetweenTradingDays(from, to)} 返回 (from, to] 区间(不含 from、含 to)的交易日数,
 * 供 MIN_HOLD_DAYS 判定:买入后经过的交易日数 ≥ {@link HardConstraintConfig#MIN_HOLD_DAYS}。
 * 日期缺失保守返 false(不误判为交易日)。
 */
class TradingCalendarServiceTest extends AbstractIntegrationTest {

    @Autowired
    TradingCalendarService tradingCalendarService;

    @Autowired
    TradingCalendarRepository tradingCalendarRepository;

    @Test
    @Transactional
    void isTradingDayReturnsFlagFromCalendar() {
        // 2026-06-22 周一交易日,2026-06-27 周六非交易日
        saveCalendar(LocalDate.of(2026, 6, 22), true);
        saveCalendar(LocalDate.of(2026, 6, 27), false);

        assertThat(tradingCalendarService.isTradingDay(LocalDate.of(2026, 6, 22))).isTrue();
        assertThat(tradingCalendarService.isTradingDay(LocalDate.of(2026, 6, 27))).isFalse();
    }

    @Test
    @Transactional
    void isTradingDayReturnsFalseForMissingDate() {
        // 表里没有的日期,保守返 false
        assertThat(tradingCalendarService.isTradingDay(LocalDate.of(2099, 1, 1))).isFalse();
    }

    @Test
    @Transactional
    void daysBetweenTradingDaysCountsTradingDaysInHalfOpenInterval() {
        // 06-22(周一)~06-26(周五)全交易日,06-27/28 周末非交易日
        for (int d = 22; d <= 26; d++) {
            saveCalendar(LocalDate.of(2026, 6, d), true);
        }
        saveCalendar(LocalDate.of(2026, 6, 27), false);
        saveCalendar(LocalDate.of(2026, 6, 28), false);

        // (06-22, 06-26] = 06-23/24/25/26 = 4 个交易日
        assertThat(tradingCalendarService.daysBetweenTradingDays(
                LocalDate.of(2026, 6, 22), LocalDate.of(2026, 6, 26))).isEqualTo(4);
    }

    @Test
    @Transactional
    void daysBetweenTradingDaysSkipsNonTradingDays() {
        // 06-25~06-29:25(四)26(五)交易日,27(六)28(日)非交易日,29(一)交易日
        saveCalendar(LocalDate.of(2026, 6, 25), true);
        saveCalendar(LocalDate.of(2026, 6, 26), true);
        saveCalendar(LocalDate.of(2026, 6, 27), false);
        saveCalendar(LocalDate.of(2026, 6, 28), false);
        saveCalendar(LocalDate.of(2026, 6, 29), true);

        // (06-25, 06-29] = 06-26/29 = 2 个交易日(跳过周末)
        assertThat(tradingCalendarService.daysBetweenTradingDays(
                LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 29))).isEqualTo(2);
    }

    private void saveCalendar(LocalDate date, boolean tradingDay) {
        TradingCalendarEntity entity = new TradingCalendarEntity();
        entity.setCalendarDate(date);
        entity.setTradingDay(tradingDay);
        tradingCalendarRepository.save(entity);
    }
}
