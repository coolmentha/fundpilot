import {useEffect, useMemo, useRef, useState} from 'react';
import {Empty, Segmented} from 'antd';
import {dispose, init} from 'klinecharts';
import {useFundIntraday} from '../api/hooks.js';

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

/** 基金详情当日分时图；数据只来自后端分钟线缓存。 */
export default function FundIntradayChart({portfolioFundId}) {
    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const [metric, setMetric] = useState('percent');
    const {data: intraday, isLoading} = useFundIntraday(portfolioFundId);
    const pointCount = intraday?.points?.length ?? 0;
    const baseNav = Number(intraday?.baseNav);
    const usePercentAxis = metric === 'percent' && Number.isFinite(baseNav) && baseNav > 0;
    const tradingSessions = intraday?.tradingSessions;
    const sessionTimes = useMemo(() => expandTradingSessions(
        Array.isArray(tradingSessions) ? tradingSessions : [],
    ), [tradingSessions]);
    const chartWidth = sessionTimes.length > 0 ? `max(100%, ${sessionTimes.length + 48}px)` : '100%';

    useEffect(() => {
        if (!containerRef.current) return undefined;
        const chart = init(containerRef.current, {locale: 'zh-CN'});
        chartRef.current = chart;
        chart.setStyles({
            candle: {
                type: 'area',
                bar: {upColor: '#EF4444', downColor: '#22C55E'},
                priceMark: {last: {show: false}},
            },
            grid: {horizontal: {color: 'rgba(51,65,85,0.4)'}, vertical: {color: 'rgba(51,65,85,0.4)'}},
            yAxis: {position: 'right', tickText: {color: '#94A3B8'}},
            xAxis: {tickText: {color: '#94A3B8'}},
        });
        const resize = () => chart.resize();
        window.addEventListener('resize', resize);
        return () => {
            window.removeEventListener('resize', resize);
            dispose(chart);
            chartRef.current = null;
        };
    }, []);

    useEffect(() => {
        const points = intraday?.points || [];
        if (!chartRef.current || points.length < 2) return;
        chartRef.current.resize();
        const times = sessionTimes.length > 0 ? sessionTimes : points.map((point) => point.time);
        const navByTime = new Map(points.map((point) => [point.time, Number(point.nav)]));
        const data = times.map((time) => {
            const nav = navByTime.get(time);
            if (!Number.isFinite(nav)) {
                return {timestamp: new Date(`${intraday.estimateDate}T${time}:00+08:00`).getTime()};
            }
            return {
                timestamp: new Date(`${intraday.estimateDate}T${time}:00+08:00`).getTime(),
                open: nav, high: nav, low: nav, close: nav, value: nav,
            };
        });
        // klinecharts 的百分比纵轴以首个可见点为 0%；首个开盘槽固定后，area 使用真实净值。
        if (usePercentAxis) {
            data[0] = {...data[0], open: baseNav, high: baseNav, low: baseNav, close: baseNav};
        }
        chartRef.current.setStyles({candle: {area: {value: usePercentAxis ? 'value' : 'close'}}});
        chartRef.current.setStyles({yAxis: {type: usePercentAxis ? 'percentage' : 'normal'}});
        chartRef.current.setScrollEnabled(!usePercentAxis);
        chartRef.current.setZoomEnabled(!usePercentAxis);
        chartRef.current.setPriceVolumePrecision(usePercentAxis ? 2 : 4, 0);
        chartRef.current.applyNewData(data);
        if (sessionTimes.length > 0) {
            chartRef.current.setBarSpace(1);
            chartRef.current.setOffsetRightDistance(0);
        }
    }, [intraday, usePercentAxis, baseNav, sessionTimes]);

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
                 style={empty ? {display: 'none'} : {width: chartWidth}}/>
        </div>
        {empty && <Empty description="暂无当日分时数据"/>}
    </>;
}
