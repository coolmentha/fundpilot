import {useEffect, useRef, useState} from 'react';
import {createChart, ColorType, CandlestickSeries, HistogramSeries, LineSeries} from 'lightweight-charts';
import {Segmented, Empty} from 'antd';
import {useFundKline} from '../api/hooks.js';

/**
 * 基金 K 线/走势图组件(行情工作台基金详情页)。
 *
 * <p>按后端返回的 chartType 分派渲染:
 * <ul>
 *   <li>{@code kline}:蜡烛图(OHLCV)+ 成交量柱(A 股惯例红涨绿跌)</li>
 *   <li>{@code nav}:累计净值折线图(主动/混合基金)</li>
 * </ul>
 *
 * <p>ETF/指数基金支持日/周/月 K 切换;主动基金只展示净值走势(周期切换对折线图无意义,隐藏)。
 *
 * <p>lightweight-charts v5 API:addSeries(SeriesDefinition, options) 而非 v4 的 addXxxSeries()。
 */
export default function KlineChart({fundId, fundSubType}) {
    const [period, setPeriod] = useState('daily');
    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const seriesRef = useRef(null);      // 主图 series(蜡烛或折线)
    const volumeRef = useRef(null);      // 成交量 series(仅 kline 模式)

    const {data: kline, isLoading} = useFundKline(fundId, period);
    const chartType = kline?.chartType || 'kline';

    // 创建/销毁 chart 实例(chartType 变化时重建,因为 series 类型不同)
    useEffect(() => {
        if (!containerRef.current) return;
        const chart = createChart(containerRef.current, {
            layout: {
                background: {type: ColorType.Solid, color: '#1E293B'},
                textColor: '#94A3B8',
                fontFamily: "'Fira Code', monospace",
            },
            grid: {
                vertLines: {color: 'rgba(51, 65, 85, 0.5)'},
                horzLines: {color: 'rgba(51, 65, 85, 0.5)'},
            },
            rightPriceScale: {borderColor: '#334155'},
            timeScale: {borderColor: '#334155', timeVisible: false},
            crosshair: {mode: 1},
            width: containerRef.current.clientWidth,
            height: 400,
        });
        chartRef.current = chart;

        // 按图表类型创建 series
        if (chartType === 'kline') {
            // A 股惯例:涨=红、跌=绿
            seriesRef.current = chart.addSeries(CandlestickSeries, {
                upColor: '#EF4444', downColor: '#22C55E',
                borderUpColor: '#EF4444', borderDownColor: '#22C55E',
                wickUpColor: '#EF4444', wickDownColor: '#22C55E',
            });
            volumeRef.current = chart.addSeries(HistogramSeries, {
                priceFormat: {type: 'volume'},
                priceScaleId: 'vol',
            });
            chart.priceScale('vol').applyOptions({
                scaleMargins: {top: 0.8, bottom: 0},
            });
        } else {
            seriesRef.current = chart.addSeries(LineSeries, {
                color: '#F59E0B', lineWidth: 2,
            });
            volumeRef.current = null;
        }

        // 响应容器宽度变化
        const resize = () => {
            if (containerRef.current) {
                chart.applyOptions({width: containerRef.current.clientWidth});
            }
        };
        window.addEventListener('resize', resize);

        return () => {
            window.removeEventListener('resize', resize);
            chart.remove();
            seriesRef.current = null;
            volumeRef.current = null;
        };
    }, [chartType]);

    // 填充数据
    useEffect(() => {
        if (!kline || !seriesRef.current) return;
        const bars = kline.bars || [];
        if (chartType === 'kline') {
            // 蜡烛图数据 + 成交量
            const candleData = bars
                .filter((b) => b.open !== null && b.open !== undefined)
                .map((b) => ({
                    time: dateToTime(b.date),
                    open: Number(b.open), high: Number(b.high),
                    low: Number(b.low), close: Number(b.close),
                }));
            const volumeData = bars
                .filter((b) => b.volume > 0)
                .map((b) => ({
                    time: dateToTime(b.date),
                    value: Number(b.volume),
                    color: Number(b.close) >= Number(b.open) ? 'rgba(239,68,68,0.4)' : 'rgba(34,197,94,0.4)',
                }));
            seriesRef.current.setData(candleData);
            volumeRef.current?.setData(volumeData);
        } else {
            // 净值折线图数据
            const lineData = bars
                .filter((b) => b.close !== null && b.close !== undefined)
                .map((b) => ({time: dateToTime(b.date), value: Number(b.close)}));
            seriesRef.current.setData(lineData);
        }
        chartRef.current?.timeScale().fitContent();
    }, [kline, chartType]);

    const isIndexLike = ['ETF', 'INDEX', 'INDEX_ENHANCED'].includes(fundSubType);

    return (
        <div className="kline-chart-wrap">
            {isIndexLike && (
                <div className="kline-toolbar">
                    <Segmented
                        size="small"
                        value={period}
                        onChange={setPeriod}
                        options={[
                            {label: '日K', value: 'daily'},
                            {label: '周K', value: 'weekly'},
                            {label: '月K', value: 'monthly'},
                        ]}
                    />
                    {kline?.benchmark && (
                        <span className="kline-benchmark muted">{kline.benchmark}</span>
                    )}
                </div>
            )}
            <div ref={containerRef} className="kline-container"/>
            {isLoading && !kline && (
                <div className="kline-loading muted">加载中...</div>
            )}
            {!isLoading && kline && (kline.bars || []).length === 0 && (
                <Empty description="暂无 K 线数据"/>
            )}
        </div>
    );
}

// Instant(ISO-8601 UTC) → lightweight-charts 时间(日期字符串 'yyyy-MM-dd')。
// 周末/节假日 K 线用 date 形式可正常显示,时间戳形式会被跳过。
function dateToTime(isoInstant) {
    if (!isoInstant) return undefined;
    return String(isoInstant).slice(0, 10);
}
