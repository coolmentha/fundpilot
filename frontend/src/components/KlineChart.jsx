import {useEffect, useRef, useState} from 'react';
import {init, dispose} from 'klinecharts';
import {Segmented, Empty} from 'antd';
import {useFundKline} from '../api/hooks.js';

/**
 * 基金 K 线/走势图组件(klinecharts v9.8.12)。
 *
 * <p>按后端返回的 chartType 分派:
 * <ul>
 *   <li>{@code kline}:蜡烛图 + MA2/5/10/20/30/60/120/250 均线(可勾选显示)+ 成交量/MACD 副图(可切换)。
 *       支持日/周/月 K 切换。A 股惯例红涨绿跌,暗色主题。模仿支付宝/同花顺 K 线:
 *       十字光标轴标签贴线、悬停浮窗显示 OHLCV+MA/MACD 数值。</li>
 *   <li>{@code nav}:累计净值面积图(主动/混合基金或指数 K 线拉取降级),无工具栏无指标。</li>
 * </ul>
 *
 * <p>生命周期:chartType 变化重建 chart;period 变化重新拉数 applyNewData;
 * MA 勾选 / 副图指标切换用 create/removeIndicator(不重建)。
 */
const MA_PERIODS = [2, 5, 10, 20, 30, 60, 120, 250];
/** MA/MACD/DIF/DEA 各线条颜色(暗色主题高对比)。indicator.lines[i] 应用于第 i 条线。 */
const LINE_COLORS = ['#F59E0B', '#3B82F6', '#A855F7', '#EC4899', '#14B8A6', '#F97316', '#84CC16', '#6366F1'];

export default function KlineChart({fundId, fundSubType}) {
    const [period, setPeriod] = useState('daily');
    /** 选中的 MA 周期集合(默认 5/10/20/30)。空集则不画均线。 */
    const [maSelected, setMaSelected] = useState(() => new Set([5, 10, 20, 30]));
    const [sub, setSub] = useState('VOL'); // 'VOL' | 'MACD' | 'NONE'

    const containerRef = useRef(null);
    const chartRef = useRef(null);
    const maPaneRef = useRef(null);
    const subPaneRef = useRef(null);
    /** MA override 进行中标记:供 window error handler 判断是否静默 klinecharts draw 竞态。 */
    const maOverridingRef = useRef(false);

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
            chart.setStyles({candle: {type: 'area', bar: {upColor: '#F59E0B', downColor: '#F59E0B'}}});
        }
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

    // 2. MA 均线(按勾选周期更新)。createIndicator 返回主蜡烛 pane id;
    //    周期变化用 overrideIndicator 原地更新(触发 regenerateFigures),避免 remove+create 频繁增删。
    //
    //    已知 klinecharts v9 库缺陷:overrideIndicator 异步重算 result(Promise.all(calcIndicator)),
    //    但 figure 再生会同步触发 draw(_listener→updateMain→drawImp),此时 result 尚未重算,
    //    drawImp 读到 undefined 报 "Cannot read properties of undefined (reading '0')"。
    //    非致命(下一帧 result 就绪后自恢复)、用户不可见(仅控制台)。MA override 期间用
    //    maOverridingRef 标记,window error handler(见 effect 1.5)静默该特定错误。
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart || chartType !== 'kline') return;
        const periods = [...maSelected].sort((a, b) => a - b);
        if (periods.length === 0) {
            if (maPaneRef.current) {
                chart.removeIndicator(maPaneRef.current, 'MA');
                maPaneRef.current = null;
            }
            return;
        }
        if (maPaneRef.current) {
            maOverridingRef.current = true;
            chart.overrideIndicator({name: 'MA', calcParams: periods}, maPaneRef.current);
            // 120ms 覆盖同步 draw + 后续 rAF 帧,然后清 flag 恢复正常错误上报
            setTimeout(() => { maOverridingRef.current = false; }, 120);
        } else {
            // isStack=true 叠加到主蜡烛 pane;calcParams 覆盖默认 [5,10,30,60]
            maPaneRef.current = chart.createIndicator({name: 'MA', calcParams: periods}, true);
        }
    }, [maSelected, chartType]);

    // 1.5 静默 klinecharts v9 MA override 期间的 draw 竞态错误。
    //    用 window.onerror 返回 true(经实测 addEventListener preventDefault 不足以抑制,
    //    window.onerror return true 才行)。仅当 maOverridingRef 为真且消息匹配时抑制,
    //    其余错误照常上报。flag 在 override 后 120ms 清除(覆盖同步 draw + 后续 rAF 帧)。
    useEffect(() => {
        const prev = window.onerror;
        window.onerror = (msg, src, line, col, err) => {
            if (maOverridingRef.current
                    && typeof msg === 'string'
                    && msg.includes("Cannot read properties of undefined (reading '0')")) {
                return true; // 抑制上报,图表下一帧 result 就绪后自恢复
            }
            return prev ? prev(msg, src, line, col, err) : false;
        };
        return () => { window.onerror = prev; };
    }, []);

    // 3. 副图指标切换(仅 kline)。removeIndicator(paneId) 整块移除副 pane,再按需新建。
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart || chartType !== 'kline') return;
        if (subPaneRef.current) {
            chart.removeIndicator(subPaneRef.current);
            subPaneRef.current = null;
        }
        if (sub !== 'NONE') {
            // isStack=false 新建独立副 pane;minHeight 防 MACD 被压扁不显示
            subPaneRef.current = chart.createIndicator(sub, false, {minHeight: 120});
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

    const toggleMa = (p) => {
        setMaSelected((prev) => {
            const next = new Set(prev);
            if (next.has(p)) next.delete(p);
            else next.add(p);
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
                        {MA_PERIODS.map((p) => (
                            <span key={p}
                                  className={`kline-ma-tag${maSelected.has(p) ? ' active' : ''}`}
                                  style={maSelected.has(p) ? {color: LINE_COLORS[MA_PERIODS.indexOf(p) % LINE_COLORS.length]} : null}
                                  onClick={() => toggleMa(p)}>{p}</span>
                        ))}
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

/**
 * 暗色主题 + A 股红涨绿跌。背景由容器 CSS 控制(#1E293B)。
 * 关键修复:
 * <ul>
 *   <li>candle.tooltip showRule=follow_cross + 浅色字 → 悬停时浮窗显示 OHLCV + MA 数值(暗色背景默认黑字不可见)</li>
 *   <li>indicator.lines/bars 浅色 + tooltip 浅色 → MACD/DIF/DEA 在暗色背景可见(MACD 之前「没显示」的根因)</li>
 *   <li>crosshair horizontal/vertical text → 轴标签贴十字线(价格/日期在线上)</li>
 *   <li>candle.type=candle_solid → 实心蜡烛,贴近支付宝/同花顺观感</li>
 * </ul>
 */
function applyDarkTheme(chart) {
    chart.setStyles({
        grid: {
            horizontal: {color: 'rgba(51,65,85,0.4)'},
            vertical: {color: 'rgba(51,65,85,0.4)'},
        },
        candle: {
            type: 'candle_solid',
            bar: {
                upColor: '#EF4444', downColor: '#22C55E',
                upBorderColor: '#EF4444', downBorderColor: '#22C55E',
                upWickColor: '#EF4444', downWickColor: '#22C55E',
            },
            priceMark: {
                show: true,
                high: {show: true, color: '#94A3B8'},
                low: {show: true, color: '#94A3B8'},
                last: {show: true, upColor: '#EF4444', downColor: '#22C55E', noChangeColor: '#94A3B8'},
            },
            tooltip: {
                showRule: 'follow_cross',     // 悬停时浮窗跟随十字光标
                showType: 'standard',         // 主 pane 左上角 legend:OHLC + MA 数值
                defaultValue: '--',
                text: {color: '#E2E8F0', size: 11},
            },
        },
        indicator: {
            // 多线条配色:MA 各周期 + MACD 的 DIF/DEA 复用前两条
            lines: LINE_COLORS.map((c) => ({color: c, size: 1})),
            bars: [{upColor: '#EF4444', downColor: '#22C55E', noChangeColor: '#64748B'}],
            tooltip: {
                showRule: 'follow_cross',
                showName: true,
                showParams: true,            // 显示 MA5:xxx / DIF:xxx
                text: {color: '#E2E8F0', size: 11},
            },
            lastValueMark: {show: true, text: {show: true, color: '#E2E8F0'}},
        },
        yAxis: {
            show: true,
            position: 'right',
            axisLine: {show: true, color: '#334155'},
            tickLine: {show: true, color: '#334155'},
            tickText: {show: true, color: '#94A3B8'},
        },
        xAxis: {
            show: true,
            axisLine: {show: true, color: '#334155'},
            tickLine: {show: true, color: '#334155'},
            tickText: {show: true, color: '#94A3B8'},
        },
        crosshair: {
            show: true,
            // 横纵轴标签贴十字线:horizontal text 显 y 轴价格、vertical text 显 x 轴日期
            horizontal: {show: true, line: {color: '#94A3B8', dashedValue: [4, 2]}, text: {show: true, color: '#1E293B', backgroundColor: '#94A3B8'}},
            vertical: {show: true, line: {color: '#94A3B8', dashedValue: [4, 2]}, text: {show: true, color: '#1E293B', backgroundColor: '#94A3B8'}},
        },
        separator: {show: true, color: '#334155'},
    });
}
