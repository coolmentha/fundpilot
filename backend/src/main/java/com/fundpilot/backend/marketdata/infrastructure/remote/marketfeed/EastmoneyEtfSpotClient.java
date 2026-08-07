package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import feign.Param;
import feign.RequestLine;

/**
 * 东方财富 ETF 实时行情客户端，对应 AKShare {@code fund_etf_spot_em}。
 *
 * <p>本接口只取 {@code f441=IOPV实时估值}，不读取场内交易价作为普通基金估值。
 */
public interface EastmoneyEtfSpotClient {

    @RequestLine("GET /api/qt/clist/get?pn={page}&pz=100&po=1&np=1"
            + "&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2"
            + "&wbp2u=%7C0%7C0%7C0%7Cweb&fid=f12"
            + "&fs=b%3AMK0021%2Cb%3AMK0022%2Cb%3AMK0023%2Cb%3AMK0024%2Cb%3AMK0827"
            + "&fields=f2%2Cf12%2Cf124%2Cf297%2Cf441")
    String fetchSpotPageRaw(@Param("page") int page);
}
