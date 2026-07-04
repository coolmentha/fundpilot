import {useEffect, useRef, useState} from 'react';
import {init, dispose} from 'klinecharts';
import {Segmented, Switch, Empty} from 'antd';
import {useFundKline} from '../api/hooks.js';

/**
 * 基金 K 线/走势图组件(klinecharts v9)。
 *
 * <p>按后端返回的 chartType 分派:
 * <ul>
 *   <li>{@code kline}:蜡烛图 + MA5/10/20/30 均线(可开关)+ 成交量副图 + MACD 副图(可切换)。
 *       支持日/周/月 K 切换。A 股惯例红涨绿跌,暗色主题。模仿支付宝基金 K 线。</li>
 *   <li>{@code nav}:累计净值面积图(主动/混合基金或指数 K 线拉取降级),无工具栏无指标。</li>
 * </ul>
 *
 * <p>生命周期:chartType 变化重建 chart;period 变化重新拉数 applyNewData;
 * MA 开关 / 副图指标切换用 create/removeIndicator(不重建)。
 */
export default function KlineChart({fundId, fundSubType}) {
    const [period, setPeriod] = useState('daily');
    const [maOn, setMaOn] = useState(true);
    const [sub, setSub] = useState('VOL'); // 'VOL' | 'MACD' | 'NONE'

    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const maPaneRef = useRef(null);
    const subPaneRef = useRef(null);

    const {data: kline, isLoading} = useFundKline(fundId, period);
    const chartType = kline?.chartType || 'kline';
    const isIndexLike = ['ETF', 'INDEX', 'INDEX_ENHANCED'].includes(fundSubType);
    const showToolbar = isIndexLike && chartType === 'kline';

    // 1. 创建/销毁 chart(chartType 变化时重建)
    useEffect(() => {
        if (!containerRef.current) return;
        const chart = init(containerRef.current);
        chartRef.current = chart;
        applyDarkTheme(chart);
        if (chartType === 'nav') {
            // 净值面积图:用 area 类型画 close 曲线
            chart.setStyles({candle: {type: 'area', bar: {upColor: '#F59E0B', downColor: '#F59E0B'}}});
        }
        // kline 模式:主蜡烛图由 init 默认创建,MA/副图由下方 effect 按状态补
        const resize = () => chart.resize();
        window.addEventListener('resize', resize);
        return () => {
            window.removeEventListener('resize', resize);
            dispose(chart);
            chartRef.current = null;
            maPaneRef.current = null;
            subPaneRef.current = null;
        };
    }, [chartType]);

    // 2. MA 均线开关(仅 kline)
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart || chartType !== 'kline') return;
        if (maOn) {
            // isStack=true 叠加到主蜡烛 pane
            maPaneRef.current = chart.createIndicator('MA', true);
        } else if (maPaneRef.current) {
            chart.removeIndicator(maPaneRef.current, 'MA');
            maPaneRef.current = null;
        }
    }, [maOn, chartType]);

    // 3. 副图指标切换(仅 kline)
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart || chartType !== 'kline') return;
        // 移除旧副图 pane
        if (subPaneRef.current) {
            chart.removeIndicator(subPaneRef.current);
            subPaneRef.current = null;
        }
        if (sub !== 'NONE') {
            subPaneRef.current = chart.createIndicator(sub);
        }
    }, [sub, chartType]);

    // 4. 填充数据
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart || !kline) return;
        const bars = kline.bars || [];
        const dataList = bars
            .filter((b) => b.close !== null && b.close !== undefined)
            .map(toKlineData);
        chart.applyNewData(dataList);
    }, [kline]);

    const height = chartType === 'kline' ? (sub === 'NONE' ? 420 : 520) : 360;

    return (
        <div className="kline-chart-wrap">
            {showToolbar && (
                <div className="kline-toolbar">
                    <Segmented size="small" value={period} onChange={setPeriod}
                               options={[{label: '日K', value: 'daily'}, {label: '周K', value: 'weekly'}, {label: '月K', value: 'monthly'}]}/>
                    <span className="kline-toolbar-group">
                        <span className="muted" style={{fontSize: 12}}>MA</span>
                        <Switch size="small" checked={maOn} onChange={setMaOn}/>
                    </span>
                    <Segmented size="small" value={sub} onChange={setSub}
                               options={[{label: '成交量', value: 'VOL'}, {label: 'MACD', value: 'MACD'}, {label: '无', value: 'NONE'}]}/>
                    {kline?.benchmark && <span className="kline-benchmark muted">{kline.benchmark}</span>}
                </div>
            )}
            <div ref={containerRef} className="kline-container" style={{height}}/>
            {isLoading && !kline && <div className="kline-loading muted">加载中...</div>}
            {!isLoading && kline && (kline.bars || []).length === 0 && <Empty description="暂无 K 线数据"/>}
        </div>
    );
}

/** 后端 Bar(ISO Instant + OHLCV) → klinecharts KLineData(timestamp 毫秒)。open/high/low 缺省取 close(净值走势用)。 */
function toKlineData(b) {
    const close = Number(b.close);
    return {
        timestamp: new Date(b.date).getTime(),
        open: b.open != null ? Number(b.open) : close,
        high: b.high != null ? Number(b.high) : close,
        low: b.low != null ? Number(b.low) : close,
        close,
        volume: b.volume ? Number(b.volume) : undefined,
    };
}

/** 暗色主题 + A 股红涨绿跌。背景由容器 CSS 控制(#1E293B)。 */
function applyDarkTheme(chart) {
    chart.setStyles({
        grid: {
            horizontal: {color: 'rgba(51,65,85,0.5)'},
            vertical: {color: 'rgba(51,65,85,0.5)'},
        },
        candle: {
            bar: {
                upColor: '#EF4444', downColor: '#22C55E',
                upBorderColor: '#EF4444', downBorderColor: '#22C55E',
                upWickColor: '#EF4444', downWickColor: '#22C55E',
            },
        },
        yAxis: {
            axisLine: {color: '#334155'},
            tickText: {color: '#94A3B8'},
            tickLine: {color: '#334155'},
        },
        xAxis: {
            axisLine: {color: '#334155'},
            tickText: {color: '#94A3B8'},
            tickLine: {color: '#334155'},
        },
        crosshair: {
            horizontal: {line: {color: '#94A3B8'}},
            vertical: {line: {color: '#94A3B8'}},
        },
    });
}
