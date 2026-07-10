package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.SinaTradingCalendarClient;
import com.fundpilot.backend.market.client.SinaTradingCalendarParser;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 交易日历同步(task 07-09 换源):从新浪财经交易日历接口提取交易日,写入 trading_calendar。
 * <p>新浪 {@code klc_td_sh.txt} 返回 KLC 自定义编码,由 {@link SinaTradingCalendarParser} 用 GraalVM JS
 * 跑 {@code hk_js_decode} 解码为交易日列表(1990-12-19 ~ 当年底)。
 *
 * <p>换源原因(原从上证指数 K 线推断的 6 个问题):
 * <ol>
 *   <li>非交易日与未同步不可区分(K 线推断只插 true 行,缺记录语义混淆)</li>
 *   <li>无法前瞻(K 线只有过去;新浪覆盖到当年底,可前瞻)</li>
 *   <li>今天是否交易日有鸡生蛋问题(K 线 15:00 后才出;新浪列表已含未来交易日)</li>
 *   <li>原同步无 @Scheduled 只能手动触发(本期加 {@code TradingCalendarSyncJob} 自动同步)</li>
 *   <li>把数据源可用性当事实(push2his 限流抽风即污染;新浪交易日历是独立数据源)</li>
 * </ol>
 *
 * <p>数据形态不变:只 INSERT 新日期(tradingDay=true),已有日期不动,幂等。
 * 调休补班周末(如 2024-09-29 周日上班)新浪正确判为非交易日--股市调休补班但休市。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingCalendarSyncService {

    private final SinaTradingCalendarClient sinaTradingCalendarClient;
    private final TradingCalendarRepository tradingCalendarRepository;

    /**
     * 从新浪同步交易日历。幂等--只 INSERT 新日期,已有日期不动。
     *
     * @return 本次新增的交易日条数
     */
    @Transactional
    public int sync() {
        String raw = sinaTradingCalendarClient.fetchTradingCalendarRaw();
        List<Instant> tradingDays = SinaTradingCalendarParser.parse(raw);

        int added = 0;
        for (Instant date : tradingDays) {
            added += tradingCalendarRepository.insertTradingDayIfAbsent(date);
        }

        log.info("交易日历同步完成(新浪源):本次新增 {} 条(已有 {} 条,新浪返回 {} 条)",
                added, tradingDays.size() - added, tradingDays.size());
        return added;
    }
}
