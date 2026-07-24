import {useEffect, useRef} from 'react';
import {Empty} from 'antd';
import {dispose, init} from 'klinecharts';
import {useFundIntraday} from '../api/hooks.js';

/** 基金详情当日分时图；数据只来自后端分钟线缓存。 */
export default function FundIntradayChart({fundId}) {
    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const {data: intraday, isLoading} = useFundIntraday(fundId);
    const pointCount = intraday?.points?.length ?? 0;

    useEffect(() => {
        if (!containerRef.current) return undefined;
        const chart = init(containerRef.current, {locale: 'zh-CN'});
        chartRef.current = chart;
        chart.setStyles({
            candle: {type: 'area', bar: {upColor: '#EF4444', downColor: '#22C55E'}},
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
        chartRef.current.applyNewData(points.map((point) => ({
            timestamp: new Date(`${intraday.estimateDate}T${point.time}:00+08:00`).getTime(),
            open: Number(point.nav), high: Number(point.nav), low: Number(point.nav), close: Number(point.nav),
        })));
    }, [intraday]);

    const empty = !isLoading && pointCount < 2;
    return <>
        <div ref={containerRef} className="intraday-chart-container" style={empty ? {display: 'none'} : undefined}/>
        {empty && <Empty description="暂无当日分时数据"/>}
    </>;
}
