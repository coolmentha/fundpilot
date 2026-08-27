import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';

const {chart, useFundKline} = vi.hoisted(() => ({
    chart: {clear: vi.fn(), resize: vi.fn(), setOption: vi.fn()},
    useFundKline: vi.fn(),
}));

vi.mock('./chartUtils.js', () => ({
    initChart: vi.fn(() => chart),
    disposeChart: vi.fn(),
    observeChartResize: vi.fn(() => vi.fn()),
}));
vi.mock('../api/hooks.js', () => ({useFundKline}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn()}));

const {default: KlineChart} = await import('./KlineChart.jsx');
const {disposeChart} = await import('./chartUtils.js');

function makeKline(chartType = 'kline', length = 40) {
    return {
        chartType,
        benchmark: '沪深300',
        bars: Array.from({length}, (_, index) => {
            const close = 1 + index * 0.01;
            return {
                date: new Date(Date.UTC(2026, 0, index + 1)).toISOString(),
                open: close - 0.01,
                high: close + 0.02,
                low: close - 0.02,
                close,
                volume: 1000 + index,
            };
        }),
    };
}

describe('KlineChart', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.clearAllMocks();
    });

    it('保留 K 线、MA 和成交量/MACD 切换', async () => {
        useFundKline.mockReturnValue({data: makeKline(), isLoading: false, isError: false, refetch: vi.fn()});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<KlineChart portfolioFundId={1} fundSubType="ETF"/>));
        const initialOption = chart.setOption.mock.lastCall[0];
        const candle = initialOption.series.find((series) => series.type === 'candlestick');
        expect(candle.large).toBe(false);
        expect(candle.data[0]).toEqual([0.99, 1, 0.98, 1.02]);
        expect(initialOption.series.find((series) => series.name === 'MA5').data.slice(0, 4))
            .toEqual([null, null, null, null]);
        expect(initialOption.series.some((series) => series.name === 'VOL')).toBe(true);
        expect(initialOption.dataZoom[0]).toMatchObject({startValue: 0, endValue: 39});

        const macd = [...container.querySelectorAll('.ant-segmented-item')]
            .find((item) => item.textContent.includes('MACD'));
        await act(async () => macd.click());
        const macdOption = chart.setOption.mock.lastCall[0];
        expect(macdOption.series.some((series) => series.name === 'DIF')).toBe(true);
        expect(macdOption.series.some((series) => series.name === 'DEA')).toBe(true);
        expect(macdOption.series.some((series) => series.name === 'VOL')).toBe(false);

        const ma2 = [...container.querySelectorAll('.kline-ma-tag')].find((item) => item.textContent === '2');
        await act(async () => ma2.click());
        expect(chart.setOption.mock.lastCall[0].series.some((series) => series.name === 'MA2')).toBe(true);
    });

    it('NAV 类型使用面积图并在卸载时销毁实例', async () => {
        useFundKline.mockReturnValue({data: makeKline('nav'), isLoading: false, isError: false, refetch: vi.fn()});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<KlineChart portfolioFundId={1} fundSubType="ACTIVE"/>));
        const option = chart.setOption.mock.lastCall[0];
        expect(option.series).toHaveLength(1);
        expect(option.series[0]).toMatchObject({name: '净值', type: 'line'});
        expect(container.querySelector('.kline-toolbar')).toBeNull();

        await act(async () => root.unmount());
        expect(disposeChart).toHaveBeenCalledTimes(1);
        root = null;
    });

    it('按日周月周期只向图表传入最近的K线', async () => {
        useFundKline.mockReturnValue({data: makeKline('kline', 400), isLoading: false, isError: false, refetch: vi.fn()});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<KlineChart portfolioFundId={1} fundSubType="ETF"/>));
        const dailyOption = chart.setOption.mock.lastCall[0];
        expect(dailyOption.xAxis.every((axis) => axis.data.length === 120)).toBe(true);
        expect(dailyOption.xAxis[0].data[0]).toBe('2026-10-08T00:00:00.000Z');
        expect(dailyOption.xAxis[0].axisLabel.formatter(dailyOption.xAxis[0].data[10], 0)).toBe('2026-10-18');
        expect(dailyOption.series.every((series) => series.data.length === 120)).toBe(true);
        expect(Number.isFinite(dailyOption.series.find((series) => series.name === 'MA30').data[0])).toBe(true);
        expect(dailyOption.tooltip.formatter([{dataIndex: 0}])).toContain('2026-10-08');
        expect(dailyOption.dataZoom[0]).toMatchObject({startValue: 0, endValue: 119});

        const weekly = [...container.querySelectorAll('.ant-segmented-item')]
            .find((item) => item.textContent.includes('周K'));
        await act(async () => weekly.click());
        const weeklyOption = chart.setOption.mock.lastCall[0];
        expect(weeklyOption.xAxis.every((axis) => axis.data.length === 104)).toBe(true);
        expect(weeklyOption.xAxis[0].data[0]).toBe('2026-10-24T00:00:00.000Z');
        expect(weeklyOption.series.every((series) => series.data.length === 104)).toBe(true);
        expect(weeklyOption.dataZoom[0]).toMatchObject({startValue: 0, endValue: 103});

        const monthly = [...container.querySelectorAll('.ant-segmented-item')]
            .find((item) => item.textContent.includes('月K'));
        await act(async () => monthly.click());
        const monthlyOption = chart.setOption.mock.lastCall[0];
        expect(monthlyOption.xAxis.every((axis) => axis.data.length === 60)).toBe(true);
        expect(monthlyOption.xAxis[0].data[0]).toBe('2026-12-07T00:00:00.000Z');
        expect(monthlyOption.series.every((series) => series.data.length === 60)).toBe(true);
        expect(monthlyOption.dataZoom[0]).toMatchObject({startValue: 0, endValue: 59});
    });
});
