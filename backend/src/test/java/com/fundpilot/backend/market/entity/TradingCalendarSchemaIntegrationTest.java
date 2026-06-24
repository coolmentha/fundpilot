package com.fundpilot.backend.market.entity;

import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * issue #3 验收证据:trading_calendar 表 + TradingCalendarEntity + Repository 的 schema 对齐。
 * <p>A 股交易日历,记录每个日期是否为交易日(含节假日剔除),MIN_HOLD_DAYS 5 个交易日判定要用。
 * 本期人工维护,不做自动同步。每个 {@code calendar_date} 全局唯一(部分唯一索引兜底)。
 */
class TradingCalendarSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    TradingCalendarRepository tradingCalendarRepository;

    @Test
    @Transactional
    void tradingCalendarPersistsIsTradingDay() {
        TradingCalendarEntity day = new TradingCalendarEntity();
        day.setCalendarDate(LocalDate.of(2026, 6, 24));
        day.setTradingDay(true);

        TradingCalendarEntity saved = tradingCalendarRepository.save(day);
        entityManager.flush();
        entityManager.clear();

        TradingCalendarEntity reloaded = tradingCalendarRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCalendarDate()).isEqualTo(LocalDate.of(2026, 6, 24));
        assertThat(reloaded.isTradingDay()).isTrue();
    }

    @Test
    @Transactional
    void duplicateCalendarDateIsRejected() {
        LocalDate date = LocalDate.of(2026, 6, 25);

        TradingCalendarEntity first = new TradingCalendarEntity();
        first.setCalendarDate(date);
        first.setTradingDay(false);
        tradingCalendarRepository.save(first);
        entityManager.flush();

        TradingCalendarEntity second = new TradingCalendarEntity();
        second.setCalendarDate(date);
        second.setTradingDay(true);

        // saveAndFlush 同步把 INSERT 推到 DB,触发 uq_trading_calendar_date 唯一约束。
        assertThatThrownBy(() -> tradingCalendarRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
