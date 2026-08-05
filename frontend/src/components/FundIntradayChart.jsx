import {useContext, useEffect, useMemo, useRef, useState} from 'react';
import {Empty, Segmented} from 'antd';
import {useFundIntraday} from '../api/hooks.js';
import {ThemeModeContext} from '../themeMode.js';
import {disposeChart, initChart, observeChartResize} from './chartUtils.js';
import {getChartColors, symmetricPercentBound} from './chartMath.js';

function minuteOf(time) {
    const match = /^(\d{2}):([0-5]\d)$/.exec(time || '');
    if (!match || Number(match[1]) > 23) return null;
    return Number(match[1]) * 60 + Number(match[2]);
}

function formatMinute(minute) {
    return `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;
}

function expandTradingSessions(sessions) {
    const times = [];
    for (const session of sessions) {
        const start = minuteOf(session.start);
        const end = minuteOf(session.end);
        if (start === null || end === null || start >= end) continue;
        for (let minute = start; minute <= end; minute += 1) times.push(formatMinute(minute));
    }
    return times;
}

function formatPercent(value) {
    if (!Number.isFinite(value)) return '-';
    return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function buildIntradayOption(times, values, percentAxis, colors) {
    const bound = percentAxis ? symmetricPercentBound(values) : null;
    const axisLabel = percentAxis
        ? (value) => formatPercent(Number(value))
        : (value) => Number(value).toFixed(4);
    const visibleLabelCount = Math.min(6, times.length);
    const interval = visibleLabelCount > 0
        ? Math.max(0, Math.ceil(times.length / visibleLabelCount) - 1)
        : 0;
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
                if (!item) return '';
                const value = values[item.dataIndex];
                return `${times[item.dataIndex]}<br/>${percentAxis ? '涨跌幅' : '净值'}: ${value === null ? '-' : (percentAxis ? formatPercent(value) : Number(value).toFixed(4))}`;
            },
        },
        xAxis: {
            type: 'category',
            boundaryGap: false,
            data: times,
            axisLine: {lineStyle: {color: colors.border}},
            axisTick: {lineStyle: {color: colors.border}},
            axisLabel: {color: colors.text, interval},
        },
        yAxis: {
            type: 'value',
            position: 'right',
            min: percentAxis ? -bound : undefined,
            max: percentAxis ? bound : undefined,
            scale: !percentAxis,
            splitNumber: 4,
            axisLine: {show: true, lineStyle: {color: colors.border}},
            axisTick: {lineStyle: {color: colors.border}},
            axisLabel: {color: colors.text, formatter: axisLabel},
            splitLine: {lineStyle: {color: colors.grid}},
        },
        dataZoom: percentAxis ? undefined : [{type: 'inside', xAxisIndex: [0], filterMode: 'none'}],
        series: [{
            name: percentAxis ? '涨跌幅' : '净值',
            type: 'line',
            data: values,
            showSymbol: false,
            symbol: 'none',
            connectNulls: false,
            lineStyle: {color: colors.primary, width: 2},
            areaStyle: {color: colors.area},
            markLine: percentAxis ? {
                silent: true,
                symbol: 'none',
                label: {show: false},
                lineStyle: {color: colors.zero, type: 'dashed', width: 1},
                data: [{yAxis: 0}],
            } : undefined,
        }],
    };
}

/** 基金详情当日分时图；数据只来自后端分钟线缓存。 */
export default function FundIntradayChart({portfolioFundId}) {
    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const [metric, setMetric] = useState('percent');
    const {themeMode} = useContext(ThemeModeContext);
    const {data: intraday, isLoading} = useFundIntraday(portfolioFundId);
    const pointCount = intraday?.points?.length ?? 0;
    const baseNav = Number(intraday?.baseNav);
    const usePercentAxis = metric === 'percent' && Number.isFinite(baseNav) && baseNav > 0;
    const tradingSessions = intraday?.tradingSessions;
    const sessionTimes = useMemo(() => expandTradingSessions(
        Array.isArray(tradingSessions) ? tradingSessions : [],
    ), [tradingSessions]);

    useEffect(() => {
        if (!containerRef.current) return undefined;
        const chart = initChart(containerRef.current);
        chartRef.current = chart;
        const stopResize = observeChartResize(containerRef.current, chart);
        return () => {
            stopResize();
            disposeChart(chart);
            if (chartRef.current === chart) chartRef.current = null;
        };
    }, []);

    useEffect(() => {
        const chart = chartRef.current;
        const points = Array.isArray(intraday?.points) ? intraday.points : [];
        if (!chart) return;
        if (points.length < 2) {
            chart.clear();
            return;
        }
        const times = sessionTimes.length > 0 ? sessionTimes : points.map((point) => point.time);
        const navByTime = new Map(points.map((point) => [point.time, Number(point.nav)]));
        const navValues = times.map((time) => {
            const nav = navByTime.get(time);
            return Number.isFinite(nav) ? nav : null;
        });
        const values = usePercentAxis
            ? navValues.map((nav) => nav === null ? null : Number(((nav / baseNav - 1) * 100).toFixed(6)))
            : navValues;
        chart.setOption(buildIntradayOption(times, values, usePercentAxis, getChartColors(themeMode)), {notMerge: true});
        chart.resize();
    }, [baseNav, intraday, sessionTimes, themeMode, usePercentAxis]);

    const empty = !isLoading && pointCount < 2;
    return <>
        {!empty && <div className="kline-toolbar">
            <Segmented size="small" value={metric} onChange={setMetric} options={[
                {label: '涨跌幅', value: 'percent'},
                {label: '净值', value: 'nav'},
            ]}/>
        </div>}
        <div className="intraday-chart-scroll">
            <div ref={containerRef} className="intraday-chart-container"
                 style={empty ? {display: 'none'} : undefined}/>
        </div>
        {empty && <Empty description="暂无当日分时数据"/>}
    </>;
}
