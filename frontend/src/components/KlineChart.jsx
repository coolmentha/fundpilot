import {useContext, useEffect, useMemo, useRef, useState} from 'react';
import {Empty, Segmented} from 'antd';
import {date as formatDate} from '../constants.js';
import {useFundKline} from '../api/hooks.js';
import {ThemeModeContext} from '../themeMode.js';
import QueryErrorState from './QueryErrorState.jsx';
import {disposeChart, initChart, observeChartResize} from './chartUtils.js';
import {calculateMacd, getChartColors, LINE_COLORS, MA_PERIODS, movingAverage} from './chartMath.js';

function finiteOr(value, fallback) {
    const number = Number(value);
    return Number.isFinite(number) ? number : fallback;
}

function toKlineData(bar) {
    const close = Number(bar.close);
    return {
        date: bar.date,
        open: finiteOr(bar.open, close),
        high: finiteOr(bar.high, close),
        low: finiteOr(bar.low, close),
        close,
        volume: finiteOr(bar.volume, 0),
    };
}

function priceText(value) {
    return Number.isFinite(value) ? value.toFixed(4) : '-';
}

function volumeText(value) {
    return Number.isFinite(value) ? value.toLocaleString('zh-CN') : '-';
}

function dateAxis(dates, colors, gridIndex = 0, showLabel = true) {
    const interval = Math.max(0, Math.ceil(dates.length / 7) - 1);
    return {
        type: 'category',
        gridIndex,
        data: dates,
        boundaryGap: true,
        axisLine: {lineStyle: {color: colors.border}},
        axisTick: {lineStyle: {color: colors.border}, alignWithLabel: true},
        axisLabel: {
            show: showLabel,
            color: colors.text,
            interval,
            formatter: (value, index) => formatDate(dates[index] || value),
        },
    };
}

function valueAxis(colors, gridIndex = 0, extra = {}) {
    return {
        type: 'value',
        gridIndex,
        scale: true,
        axisLine: {show: true, lineStyle: {color: colors.border}},
        axisTick: {lineStyle: {color: colors.border}},
        axisLabel: {color: colors.text, formatter: (value) => Number(value).toFixed(4)},
        splitLine: {lineStyle: {color: colors.grid}},
        ...extra,
    };
}

function buildNavOption(bars, colors) {
    const dates = bars.map((bar) => bar.date);
    return {
        animation: false,
        grid: {left: 8, right: 48, top: 16, bottom: 28, containLabel: true},
        tooltip: {
            trigger: 'axis',
            axisPointer: {type: 'cross'},
            backgroundColor: colors.tooltipBackground,
            borderColor: colors.tooltipBorder,
            textStyle: {color: colors.strongText},
            formatter: (params) => {
                const item = Array.isArray(params) ? params[0] : params;
                const bar = item ? bars[item.dataIndex] : null;
                return bar ? `${formatDate(bar.date)}<br/>净值: ${priceText(bar.close)}` : '';
            },
        },
        xAxis: dateAxis(dates, colors),
        yAxis: valueAxis(colors),
        dataZoom: [{type: 'inside', xAxisIndex: [0], filterMode: 'none'}],
        series: [{
            name: '净值',
            type: 'line',
            data: bars.map((bar) => bar.close),
            showSymbol: false,
            lineStyle: {color: colors.primary, width: 2},
            areaStyle: {color: colors.area},
        }],
    };
}

function buildKlineTooltip(bars, periods, sub, macd, params) {
    const item = Array.isArray(params) ? params.find((entry) => entry.dataIndex !== undefined) : params;
    const index = item?.dataIndex;
    const bar = index === undefined ? null : bars[index];
    if (!bar) return '';
    const lines = [
        formatDate(bar.date),
        `开: ${priceText(bar.open)}　高: ${priceText(bar.high)}　低: ${priceText(bar.low)}　收: ${priceText(bar.close)}`,
        `成交量: ${volumeText(bar.volume)}`,
    ];
    for (const period of periods) lines.push(`MA${period}: ${priceText(movingAverage(bars.map((value) => value.close), period)[index])}`);
    if (sub === 'VOL') lines.push(`VOL: ${volumeText(bar.volume)}`);
    if (sub === 'MACD') {
        lines.push(`DIF: ${priceText(macd.dif[index])}`);
        lines.push(`DEA: ${priceText(macd.dea[index])}`);
        lines.push(`MACD: ${priceText(macd.histogram[index])}`);
    }
    return lines.join('<br/>');
}

function buildKlineOption(bars, maSelected, sub, colors) {
    const dates = bars.map((bar) => bar.date);
    const closes = bars.map((bar) => bar.close);
    const periods = [...maSelected].sort((a, b) => a - b);
    const showSub = sub !== 'NONE';
    const macd = calculateMacd(closes);
    const grids = showSub
        ? [
            {left: 8, right: 48, top: 16, bottom: '29%', containLabel: true},
            {left: 8, right: 48, top: '75%', height: '19%', containLabel: true},
        ]
        : [{left: 8, right: 48, top: 16, bottom: 28, containLabel: true}];
    const xAxes = [dateAxis(dates, colors, 0, !showSub)];
    const yAxes = [valueAxis(colors)];
    if (showSub) {
        xAxes.push(dateAxis(dates, colors, 1));
        yAxes.push(sub === 'VOL'
            ? valueAxis(colors, 1, {min: 0, axisLabel: {color: colors.text, formatter: volumeText}})
            : valueAxis(colors, 1, {axisLabel: {color: colors.text, formatter: (value) => Number(value).toFixed(4)}}));
    }
    const series = [{
        name: 'K线',
        type: 'candlestick',
        data: bars.map((bar) => [bar.open, bar.close, bar.low, bar.high]),
        itemStyle: {
            color: colors.up,
            color0: colors.down,
            borderColor: colors.up,
            borderColor0: colors.down,
        },
    }];
    for (const period of periods) {
        series.push({
            name: `MA${period}`,
            type: 'line',
            data: movingAverage(closes, period),
            showSymbol: false,
            connectNulls: false,
            lineStyle: {color: LINE_COLORS[MA_PERIODS.indexOf(period) % LINE_COLORS.length], width: 1},
            xAxisIndex: 0,
            yAxisIndex: 0,
        });
    }
    if (sub === 'VOL') {
        series.push({
            name: 'VOL',
            type: 'bar',
            xAxisIndex: 1,
            yAxisIndex: 1,
            data: bars.map((bar) => ({
                value: bar.volume,
                itemStyle: {color: bar.close >= bar.open ? colors.up : colors.down},
            })),
        });
    }
    if (sub === 'MACD') {
        series.push(
            {
                name: 'MACD',
                type: 'bar',
                xAxisIndex: 1,
                yAxisIndex: 1,
                data: macd.histogram.map((value) => ({
                    value,
                    itemStyle: {color: Number(value) >= 0 ? colors.up : colors.down},
                })),
            },
            {name: 'DIF', type: 'line', xAxisIndex: 1, yAxisIndex: 1, data: macd.dif, showSymbol: false, lineStyle: {color: '#F59E0B'}},
            {name: 'DEA', type: 'line', xAxisIndex: 1, yAxisIndex: 1, data: macd.dea, showSymbol: false, lineStyle: {color: '#3B82F6'}},
        );
    }
    return {
        animation: false,
        grid: grids,
        tooltip: {
            trigger: 'axis',
            axisPointer: {type: 'cross'},
            backgroundColor: colors.tooltipBackground,
            borderColor: colors.tooltipBorder,
            textStyle: {color: colors.strongText},
            formatter: (params) => buildKlineTooltip(bars, periods, sub, macd, params),
        },
        axisPointer: {link: [{xAxisIndex: 'all'}]},
        xAxis: xAxes,
        yAxis: yAxes,
        dataZoom: [{type: 'inside', xAxisIndex: showSub ? [0, 1] : [0], filterMode: 'none'}],
        series,
    };
}

export default function KlineChart({portfolioFundId, fundSubType}) {
    const [period, setPeriod] = useState('daily');
    const [maSelected, setMaSelected] = useState(() => new Set([5, 10, 20, 30]));
    const [sub, setSub] = useState('VOL');
    const {themeMode} = useContext(ThemeModeContext);
    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const {data: kline, isLoading, isError, refetch} = useFundKline(portfolioFundId, period);
    const chartType = kline?.chartType;
    const isIndexLike = ['ETF', 'INDEX', 'INDEX_ENHANCED'].includes(fundSubType);
    const bars = useMemo(() => (kline?.bars || [])
        .filter((bar) => Number.isFinite(Number(bar.close)))
        .map(toKlineData), [kline]);
    const hasData = !!chartType && bars.length > 0;
    const showToolbar = isIndexLike && chartType === 'kline' && hasData;

    useEffect(() => {
        if (!containerRef.current || !chartType) return undefined;
        const chart = initChart(containerRef.current);
        chartRef.current = chart;
        const stopResize = observeChartResize(containerRef.current, chart);
        return () => {
            stopResize();
            disposeChart(chart);
            if (chartRef.current === chart) chartRef.current = null;
        };
    }, [chartType]);

    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;
        if (!kline || bars.length === 0) {
            chart.clear();
            return;
        }
        const colors = getChartColors(themeMode);
        const option = chartType === 'nav'
            ? buildNavOption(bars, colors)
            : buildKlineOption(bars, maSelected, sub, colors);
        chart.setOption(option, {notMerge: true});
        chart.resize();
    }, [bars, chartType, kline, maSelected, sub, themeMode]);

    const height = chartType === 'kline' ? (sub === 'NONE' ? 420 : 520) : 360;

    const toggleMa = (value) => {
        setMaSelected((previous) => {
            const next = new Set(previous);
            if (next.has(value)) next.delete(value);
            else next.add(value);
            return next;
        });
    };

    return (
        <div className="kline-chart-wrap">
            {showToolbar && (
                <div className="kline-toolbar">
                    <Segmented size="small" value={period} onChange={setPeriod}
                               options={[{label: '日K', value: 'daily'}, {label: '周K', value: 'weekly'}, {label: '月K', value: 'monthly'}]}/>
                    <span className="kline-ma-group">
                        <span className="muted" style={{fontSize: 12}}>MA</span>
                        {MA_PERIODS.map((value) => (
                            <span key={value}
                                  className={`kline-ma-tag${maSelected.has(value) ? ' active' : ''}`}
                                  style={maSelected.has(value) ? {color: LINE_COLORS[MA_PERIODS.indexOf(value) % LINE_COLORS.length]} : null}
                                  role="button"
                                  tabIndex={0}
                                  aria-pressed={maSelected.has(value)}
                                  aria-label={`MA${value} 均线${maSelected.has(value) ? '已显示' : '已隐藏'},点击切换`}
                                  onClick={() => toggleMa(value)}
                                  onKeyDown={(event) => {
                                      if (event.key === 'Enter' || event.key === ' ') {
                                          event.preventDefault();
                                          toggleMa(value);
                                      }
                                  }}>{value}</span>
                        ))}
                    </span>
                    <Segmented size="small" value={sub} onChange={setSub}
                               options={[{label: '成交量', value: 'VOL'}, {label: 'MACD', value: 'MACD'}, {label: '无', value: 'NONE'}]}/>
                    {kline?.benchmark && <span className="kline-benchmark muted">{kline.benchmark}</span>}
                </div>
            )}
            <div ref={containerRef} className="kline-container" style={{height}}/>
            {isLoading && !kline && <div className="kline-loading muted">加载中...</div>}
            {isError && <QueryErrorState onRetry={refetch} description="K 线数据加载失败"/>}
            {!isLoading && !isError && kline && bars.length === 0 && <Empty description="暂无 K 线数据"/>}
        </div>
    );
}
