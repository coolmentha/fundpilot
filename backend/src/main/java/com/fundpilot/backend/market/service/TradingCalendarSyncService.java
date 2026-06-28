package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.EastmoneyKlineClient;
import com.fundpilot.backend.market.client.EastmoneyJsParser;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 交易日历同步:从东方财富上证指数日K线提取交易日,写入 trading_calendar。
 * <p>上证指数(secid=1.000001)只在交易日发布 K 线,周末节假日自动跳过——天然交易日历。
 * 只 INSERT 新日期,不覆盖已有(true/false 不变),支持幂等多次同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingCalendarSyncService {

    /** 上证指数 secid。 */
    private static final String SH_COMPOSITE = "1.000001";
    /** 拉足够多 K 线(覆盖全部历史+未来数年)。 */
    private static final String LMT = "10000";

    private final EastmoneyKlineClient eastmoneyKlineClient;
    private final TradingCalendarRepository tradingCalendarRepository;

    /**
     * 从东方财富同步交易日历。幂等——只 INSERT 新日期,已有日期不动。
     *
     * @return 本次新增的交易日条数
     */
    @Transactional
    public int sync() {
        Set<Instant> existing = loadExistingDates();
        String raw = eastmoneyKlineClient.fetchKlineRaw(SH_COMPOSITE, LMT);
        IndexKline kline = EastmoneyJsParser.parseIndexKline(raw);

        int added = 0;
        for (IndexKline.Bar bar : kline.bars()) {
            Instant date = bar.date();
            if (existing.contains(date)) {
                continue;
            }
            TradingCalendarEntity entity = new TradingCalendarEntity();
            entity.setCalendarDate(date);
            entity.setTradingDay(true);
            tradingCalendarRepository.save(entity);
            existing.add(date);
            added++;
        }

        log.info("交易日历同步完成:本次新增 {} 条(已有 {} 条,K线总数 {} 条)",
                added, existing.size() - added, kline.bars().size());
        return added;
    }

    private Set<Instant> loadExistingDates() {
        Set<Instant> dates = new HashSet<>();
        tradingCalendarRepository.findAll().forEach(e -> dates.add(e.getCalendarDate()));
        return dates;
    }
}
