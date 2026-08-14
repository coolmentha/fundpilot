package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeMarketOverviewQueryHandlerTest {

    private static final Instant TRADING_DATE = Instant.parse("2026-07-10T00:00:00Z");

    @Test
    void findStatus_按交易日历和北京时间区分市场状态() {
        assertState("2026-07-10T01:00:00Z", true, "PRE_OPEN");
        assertState("2026-07-10T01:30:00Z", true, "TRADING");
        assertState("2026-07-10T03:30:00Z", true, "LUNCH_BREAK");
        assertState("2026-07-10T05:00:00Z", true, "TRADING");
        assertState("2026-07-10T07:00:00Z", true, "CLOSED");
        assertState("2026-07-11T02:00:00Z", false, "NON_TRADING_DAY");
    }

    @Test
    void findStatus_透传最旧成功快照时间() {
        RealtimeMarketOverviewGateway cache = mock(RealtimeMarketOverviewGateway.class);
        TradingCalendarQueryHandler calendar = mock(TradingCalendarQueryHandler.class);
        Instant updatedAt = Instant.parse("2026-07-10T06:59:30Z");
        when(calendar.isTradingDay(any())).thenReturn(true);
        when(cache.findUpdatedAt()).thenReturn(updatedAt);

        var result = new RealtimeMarketOverviewQueryHandler(cache, calendar,
                Clock.fixed(Instant.parse("2026-07-10T07:00:00Z"), ZoneOffset.UTC)).findStatus();

        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void findVolumePrice_覆盖量比阈值和涨跌方向() {
        Map<Case, String> cases = Map.of(
                new Case("0.01", "1.5"), "HIGH_UP",
                new Case("0.01", "0.5"), "LOW_UP",
                new Case("-0.01", "1.5"), "HIGH_DOWN",
                new Case("-0.01", "0.5"), "LOW_DOWN",
                new Case("0.01", "1.49"), "NORMAL_UP",
                new Case("-0.01", "0.51"), "NORMAL_DOWN",
                new Case("0", "2"), "FLAT");

        cases.forEach((input, expected) -> assertVolumePrice(input, expected));
    }

    @Test
    void findVolumePrice_交易时段快照超过两分钟_返回不可用() {
        Instant now = Instant.parse("2026-07-10T05:30:00Z");
        RealtimeMarketOverviewGateway cache = marketCache("0.01", "1.5",
                Instant.parse("2026-07-10T05:27:59Z"));
        TradingCalendarQueryHandler calendar = tradingCalendar(true);

        var result = new RealtimeMarketOverviewQueryHandler(cache, calendar,
                Clock.fixed(now, ZoneOffset.UTC)).findVolumePrice();

        assertThat(result.state().name()).isEqualTo("UNAVAILABLE");
        assertThat(result.phase().name()).isEqualTo("INTRADAY_ESTIMATE");
        assertThat(result.changePct()).isNull();
        assertThat(result.volumeRatio()).isNull();
        assertThat(result.quoteTime()).isEqualTo(Instant.parse("2026-07-10T05:27:59Z"));
    }

    @Test
    void findVolumePrice_午休不按自然流逝判过期() {
        Instant now = Instant.parse("2026-07-10T04:30:00Z");
        RealtimeMarketOverviewGateway cache = marketCache("0.01", "1.2",
                Instant.parse("2026-07-10T03:30:00Z"));
        TradingCalendarQueryHandler calendar = tradingCalendar(true);

        var result = new RealtimeMarketOverviewQueryHandler(cache, calendar,
                Clock.fixed(now, ZoneOffset.UTC)).findVolumePrice();

        assertThat(result.state().name()).isEqualTo("NORMAL_UP");
        assertThat(result.phase().name()).isEqualTo("INTRADAY_ESTIMATE");
    }

    @Test
    void findVolumePrice_盘前和非交易日只接受最近交易日() {
        Instant previousTradingDay = Instant.parse("2026-07-09T00:00:00Z");
        RealtimeMarketOverviewGateway preOpenCache = marketCache("0.01", "1.2",
                Instant.parse("2026-07-09T07:00:00Z"));
        TradingCalendarQueryHandler preOpenCalendar = tradingCalendar(true);
        when(preOpenCalendar.latestBefore(TRADING_DATE)).thenReturn(Optional.of(previousTradingDay));

        var preOpen = new RealtimeMarketOverviewQueryHandler(preOpenCache, preOpenCalendar,
                Clock.fixed(Instant.parse("2026-07-10T01:00:00Z"), ZoneOffset.UTC)).findVolumePrice();

        assertThat(preOpen.state().name()).isEqualTo("NORMAL_UP");
        assertThat(preOpen.phase().name()).isEqualTo("CLOSED");

        RealtimeMarketOverviewGateway weekendCache = marketCache("-0.01", "1.6",
                Instant.parse("2026-07-10T07:00:00Z"));
        TradingCalendarQueryHandler weekendCalendar = tradingCalendar(false);
        when(weekendCalendar.latestOnOrBefore(Instant.parse("2026-07-11T00:00:00Z")))
                .thenReturn(Optional.of(TRADING_DATE));

        var weekend = new RealtimeMarketOverviewQueryHandler(weekendCache, weekendCalendar,
                Clock.fixed(Instant.parse("2026-07-11T02:00:00Z"), ZoneOffset.UTC)).findVolumePrice();

        assertThat(weekend.state().name()).isEqualTo("HIGH_DOWN");
        assertThat(weekend.phase().name()).isEqualTo("CLOSED");
    }

    @Test
    void findVolumePrice_行情日期不是应展示交易日_返回不可用() {
        RealtimeMarketOverviewGateway cache = marketCache("0.01", "1.2",
                Instant.parse("2026-07-08T07:00:00Z"));
        TradingCalendarQueryHandler calendar = tradingCalendar(true);
        when(calendar.latestBefore(TRADING_DATE))
                .thenReturn(Optional.of(Instant.parse("2026-07-09T00:00:00Z")));

        var result = new RealtimeMarketOverviewQueryHandler(cache, calendar,
                Clock.fixed(Instant.parse("2026-07-10T01:00:00Z"), ZoneOffset.UTC)).findVolumePrice();

        assertThat(result.state().name()).isEqualTo("UNAVAILABLE");
    }

    private static void assertState(String now, boolean tradingDay, String expected) {
        RealtimeMarketOverviewGateway cache = mock(RealtimeMarketOverviewGateway.class);
        TradingCalendarQueryHandler calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(any())).thenReturn(tradingDay);
        var handler = new RealtimeMarketOverviewQueryHandler(cache, calendar,
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));

        assertThat(handler.findStatus().marketState().name()).isEqualTo(expected);
    }

    private static void assertVolumePrice(Case input, String expected) {
        Instant now = Instant.parse("2026-07-10T05:30:00Z");
        RealtimeMarketOverviewGateway cache = marketCache(input.changePct(), input.volumeRatio(),
                Instant.parse("2026-07-10T05:29:30Z"));
        var result = new RealtimeMarketOverviewQueryHandler(cache, tradingCalendar(true),
                Clock.fixed(now, ZoneOffset.UTC)).findVolumePrice();

        assertThat(result.state().name()).isEqualTo(expected);
        assertThat(result.phase().name()).isEqualTo("INTRADAY_ESTIMATE");
    }

    private static RealtimeMarketOverviewGateway marketCache(String changePct, String volumeRatio,
                                                              Instant quoteTime) {
        RealtimeMarketOverviewGateway cache = mock(RealtimeMarketOverviewGateway.class);
        when(cache.findMarketVolumePrice()).thenReturn(new RealtimeMarketOverviewGateway.MarketVolumePrice(
                new BigDecimal(changePct), new BigDecimal(volumeRatio), quoteTime));
        return cache;
    }

    private static TradingCalendarQueryHandler tradingCalendar(boolean tradingDay) {
        TradingCalendarQueryHandler calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(any())).thenReturn(tradingDay);
        when(calendar.latestOnOrBefore(TRADING_DATE)).thenReturn(Optional.of(TRADING_DATE));
        return calendar;
    }

    private record Case(String changePct, String volumeRatio) {}
}
