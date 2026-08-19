import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';
import {ThemeModeContext} from '../themeMode.js';

const {chart, useFundIntraday} = vi.hoisted(() => ({
    chart: {clear: vi.fn(), resize: vi.fn(), setOption: vi.fn()},
    useFundIntraday: vi.fn(),
}));

vi.mock('./chartUtils.js', () => ({
    initChart: vi.fn(() => chart),
    disposeChart: vi.fn(),
    observeChartResize: vi.fn(() => vi.fn()),
}));
vi.mock('../api/hooks.js', () => ({useFundIntraday}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn()}));

const {default: FundIntradayChart} = await import('./FundIntradayChart.jsx');

describe('FundIntradayChart', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.clearAllMocks();
    });

    it('百分比轴动态对称，净值模式保留真实净值', async () => {
        useFundIntraday.mockReturnValue({data: {
            estimateDate: '2026-07-24', baseNav: '1.0000',
            points: [{time: '09:30', nav: '1.0400'}, {time: '09:31', nav: '0.9800'}],
        }, isLoading: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<FundIntradayChart portfolioFundId={1}/>));
        const percentOption = chart.setOption.mock.lastCall[0];
        expect(percentOption.series[0].data).toEqual([4, -2]);
        expect(percentOption.yAxis).toMatchObject({min: -4, max: 4});
        expect(percentOption.series[0].lineStyle.color).toBe('#22C55E');
        expect(percentOption.series[0].areaStyle).toEqual({
            origin: 'start',
            opacity: 0.18,
            color: {
                type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                colorStops: [
                    {offset: 0, color: '#22C55E'},
                    {offset: 1, color: 'transparent'},
                ],
            },
        });
        expect(percentOption.series[0].markLine.data).toEqual([{yAxis: 0}]);

        const navOption = [...container.querySelectorAll('.ant-segmented-item')]
            .find((item) => item.textContent.includes('净值'));
        await act(async () => navOption.click());
        const navChartOption = chart.setOption.mock.lastCall[0];
        expect(navChartOption.series[0].data).toEqual([1.04, 0.98]);
        expect(navChartOption.series[0].lineStyle.color).toBe('#22C55E');
        expect(navChartOption.yAxis.min).toBeUndefined();
        expect(navChartOption.series[0].markLine).toBeUndefined();
        expect(navChartOption.dataZoom[0]).toMatchObject({type: 'inside', xAxisIndex: [0]});
    });

    it.each([
        ['深色上涨', '1.0200', 'dark', '#EF4444'],
        ['深色持平', '1.0000', 'dark', '#64748B'],
        ['浅色上涨', '1.0200', 'light', '#DC2626'],
    ])('最新有效点%s时使用对应线色和阴影', async (_, latestNav, themeMode, color) => {
        useFundIntraday.mockReturnValue({data: {
            estimateDate: '2026-07-24', baseNav: '1.0000',
            tradingSessions: [{start: '09:30', end: '09:32'}],
            points: [{time: '09:30', nav: '1.0000'}, {time: '09:31', nav: latestNav}],
        }, isLoading: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(
            <ThemeModeContext.Provider value={{themeMode}}>
                <FundIntradayChart portfolioFundId={1}/>
            </ThemeModeContext.Provider>,
        ));
        const series = chart.setOption.mock.lastCall[0].series[0];
        expect(series.lineStyle.color).toBe(color);
        expect(series.areaStyle.color.colorStops[0].color).toBe(color);
    });

    it('按交易段跳过午休，未来分钟保留类目但值为空', async () => {
        useFundIntraday.mockReturnValue({data: {
            estimateDate: '2026-07-24', baseNav: '1.0000',
            tradingSessions: [{start: '09:30', end: '09:32'}, {start: '13:00', end: '13:01'}],
            points: [
                {time: '09:30', nav: '1.0010'},
                {time: '09:31', nav: '1.0020'},
                {time: '13:00', nav: '1.0030'},
            ],
        }, isLoading: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<FundIntradayChart portfolioFundId={1}/>));
        const option = chart.setOption.mock.lastCall[0];
        expect(option.xAxis.data).toEqual(['09:30', '09:31', '09:32', '13:00', '13:01']);
        expect(option.xAxis.data).not.toContain('11:30');
        expect(option.series[0].data.slice(0, 3)).toEqual([0.1, 0.2, null]);
        expect(option.series[0].data.slice(-1)[0]).toBeNull();
    });
});
