package com.fundpilot.backend.market.client;

import java.time.LocalDate;
import java.util.List;

/**
 * 指数 K 线数据:东方财富 push2his.eastmoney.com 解析结果。
 *
 * @param bars OHLCV 柱线列表,按日期升序
 */
public record IndexKline(List<Bar> bars) {

    public record Bar(LocalDate date, java.math.BigDecimal open, java.math.BigDecimal close,
                      java.math.BigDecimal high, java.math.BigDecimal low, long volume) {
    }
}