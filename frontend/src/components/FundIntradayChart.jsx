import {useEffect, useRef, useState} from 'react';
import {Empty, Segmented} from 'antd';
import {dispose, init} from 'klinecharts';
import {useFundIntraday} from '../api/hooks.js';

/** 基金详情当日分时图；数据只来自后端分钟线缓存。 */
export default function FundIntradayChart({fundId}) {
    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const [metric, setMetric] = useState('percent');
    const {data: intraday, isLoading} = useFundIntraday(fundId);
    const pointCount = intraday?.points?.length ?? 0;
    const baseNav = Number(intraday?.baseNav);
    const usePercentAxis = metric === 'percent' && Number.isFinite(baseNav) && baseNav > 0;

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
        const data = points.map((point) => ({
            timestamp: new Date(`${intraday.estimateDate}T${point.time}:00+08:00`).getTime(),
            open: Number(point.nav), high: Number(point.nav), low: Number(point.nav), close: Number(point.nav),
        }));
        // klinecharts 的百分比纵轴以首个可见点为 0%；插入并固定基准净值确保其对应后端 baseNav。
        if (usePercentAxis) {
            data.unshift({timestamp: data[0].timestamp - 60_000, open: baseNav, high: baseNav, low: baseNav, close: baseNav});
        }
        chartRef.current.setStyles({yAxis: {type: usePercentAxis ? 'percentage' : 'normal'}});
        chartRef.current.setScrollEnabled(!usePercentAxis);
        chartRef.current.setZoomEnabled(!usePercentAxis);
        chartRef.current.setPriceVolumePrecision(usePercentAxis ? 2 : 4, 0);
        chartRef.current.applyNewData(data);
    }, [intraday, usePercentAxis, baseNav]);

    const empty = !isLoading && pointCount < 2;
    return <>
        {!empty && <div className="kline-toolbar">
            <Segmented size="small" value={metric} onChange={setMetric} options={[
                {label: '涨跌幅', value: 'percent'},
                {label: '净值', value: 'nav'},
            ]}/>
        </div>}
        <div ref={containerRef} className="intraday-chart-container" style={empty ? {display: 'none'} : undefined}/>
        {empty && <Empty description="暂无当日分时数据"/>}
    </>;
}
