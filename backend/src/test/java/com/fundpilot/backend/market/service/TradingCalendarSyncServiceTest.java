package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.SinaTradingCalendarClient;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingCalendarSyncServiceTest {

    @Mock private SinaTradingCalendarClient sinaTradingCalendarClient;
    @Mock private TradingCalendarRepository tradingCalendarRepository;
    @InjectMocks private TradingCalendarSyncService service;

    @Test
    void sync_逐日使用原子插入并累计实际新增数() throws IOException {
        when(sinaTradingCalendarClient.fetchTradingCalendarRaw()).thenReturn(loadSample());
        when(tradingCalendarRepository.findMaxCalendarDate()).thenReturn(Optional.empty());
        AtomicInteger calls = new AtomicInteger();
        when(tradingCalendarRepository.insertTradingDayIfAbsent(any()))
                .thenAnswer(invocation -> calls.getAndIncrement() == 0 ? 1 : 0);

        int added = service.sync();

        assertThat(added).isEqualTo(1);
        assertThat(calls).hasValueGreaterThan(8000);
    }

    @Test
    void sync_非空表只写当前最大日期之后的数据() throws IOException {
        String raw = loadSample();
        List<Instant> dates = com.fundpilot.backend.market.client.SinaTradingCalendarParser.parse(raw);
        Instant maxDate = dates.get(dates.size() - 2);
        when(sinaTradingCalendarClient.fetchTradingCalendarRaw()).thenReturn(raw);
        when(tradingCalendarRepository.findMaxCalendarDate()).thenReturn(Optional.of(maxDate));
        when(tradingCalendarRepository.insertTradingDayIfAbsent(any())).thenReturn(1);

        int added = service.sync();

        assertThat(added).isEqualTo(1);
        verify(tradingCalendarRepository).insertTradingDayIfAbsent(dates.get(dates.size() - 1));
    }

    @Test
    void syncFull_忽略当前最大日期并保留全量补写能力() throws IOException {
        String raw = loadSample();
        List<Instant> dates = com.fundpilot.backend.market.client.SinaTradingCalendarParser.parse(raw);
        when(sinaTradingCalendarClient.fetchTradingCalendarRaw()).thenReturn(raw);
        when(tradingCalendarRepository.insertTradingDayIfAbsent(any())).thenReturn(0);

        int added = service.syncFull();

        assertThat(added).isZero();
        verify(tradingCalendarRepository, times(dates.size())).insertTradingDayIfAbsent(any());
        verify(tradingCalendarRepository, times(0)).findMaxCalendarDate();
    }

    private String loadSample() throws IOException {
        try (var in = getClass().getResourceAsStream("/sina/klc_td_sh_sample.txt")) {
            if (in == null) {
                throw new IllegalStateException("测试夹具 sina/klc_td_sh_sample.txt 未找到");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
