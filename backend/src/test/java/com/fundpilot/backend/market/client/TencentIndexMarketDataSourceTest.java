package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TencentIndexMarketDataSourceTest {

    private static final String RAW = """
            kline_dayqfq={"code":0,"data":{"sh000300":{"day":[
            ["2026-01-02","100","101","102","99","100",{}],
            ["2026-01-05","101","103","104","100","200",{}]
            ]}}}
            """;

    @Test
    void toTencentSymbol_按交易所secid映射() {
        assertThat(TencentIndexMarketDataSource.toTencentSymbol("1.000300")).isEqualTo("sh000300");
        assertThat(TencentIndexMarketDataSource.toTencentSymbol("0.399001")).isEqualTo("sz399001");
        assertThat(TencentIndexMarketDataSource.toTencentSymbol("000300")).isEqualTo("sh000300");
    }

    @Test
    void CSI指数直接跳过腾讯源() {
        TencentIndexClient client = mock(TencentIndexClient.class);

        assertThatThrownBy(() -> new TencentIndexMarketDataSource(client)
                .fetchIndexKline("2.930713", "400"))
                .isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(client);
    }

    @Test
    void fetchIndexKline_传腾讯symbol并保留最近limit根() {
        AtomicReference<String> symbol = new AtomicReference<>();
        TencentIndexClient client = (value, start, end) -> {
            symbol.set(value);
            return RAW;
        };

        IndexKline kline = new TencentIndexMarketDataSource(client)
                .fetchIndexKline("1.000300", "1");

        assertThat(symbol.get()).isEqualTo("sh000300");
        assertThat(kline.bars()).hasSize(1);
        assertThat(kline.bars().getFirst().close()).isEqualByComparingTo("103");
        assertThat(kline.bars().getFirst().date()).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
    }

    @Test
    void 单页超640根上限时向前翻页合并() {
        LocalDate page1First = LocalDate.parse("2025-01-06");
        List<String> pages = List.of(
                rawPage("sh000300", page1First, 640),
                rawPage("sh000300", LocalDate.parse("2024-01-01"), 10));
        AtomicInteger call = new AtomicInteger();
        List<String> endDates = new ArrayList<>();
        List<String> startDates = new ArrayList<>();
        TencentIndexClient client = (symbol, start, end) -> {
            startDates.add(start);
            endDates.add(end);
            return pages.get(call.getAndIncrement());
        };

        IndexKline kline = new TencentIndexMarketDataSource(client)
                .fetchIndexKlineWithPeriod("1.000300", "101", "1200");

        assertThat(call.get()).isEqualTo(2);
        assertThat(endDates.get(1)).isEqualTo("2025-01-05");
        assertThat(startDates.get(1)).isEqualTo(startDates.get(0));
        assertThat(kline.bars()).hasSize(650);
        assertThat(kline.bars().getFirst().date()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(kline.bars().getLast().date())
                .isEqualTo(page1First.plusDays(639).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void 翻页达到MAX_PAGES上限即停止() {
        String fullPage = rawPage("sh000300", LocalDate.parse("2025-01-06"), 640);
        AtomicInteger call = new AtomicInteger();
        TencentIndexClient client = (symbol, start, end) -> {
            call.getAndIncrement();
            return fullPage;
        };

        IndexKline kline = new TencentIndexMarketDataSource(client)
                .fetchIndexKlineWithPeriod("1.000300", "101", "99999");

        assertThat(call.get()).isEqualTo(8);
        assertThat(kline.bars()).hasSize(640);
    }

    private static String rawPage(String symbol, LocalDate firstDate, int count) {
        StringJoiner day = new StringJoiner(",");
        LocalDate d = firstDate;
        for (int i = 0; i < count; i++) {
            day.add("[\"" + d + "\",\"100\",\"101\",\"102\",\"99\",\"100\",{}]");
            d = d.plusDays(1);
        }
        return "kline_dayqfq={\"code\":0,\"data\":{\"" + symbol + "\":{\"day\":[" + day + "]}}}";
    }
}
