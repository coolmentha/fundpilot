import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';

const {chart, useFundIntraday} = vi.hoisted(() => ({
    chart: {resize: vi.fn(), getSize: vi.fn(() => ({width: 1000})), setStyles: vi.fn(), setScrollEnabled: vi.fn(), setZoomEnabled: vi.fn(), setBarSpace: vi.fn(), setOffsetRightDistance: vi.fn(), setPriceVolumePrecision: vi.fn(), applyNewData: vi.fn()},
    useFundIntraday: vi.fn(),
}));

vi.mock('klinecharts', () => ({init: vi.fn(() => chart), dispose: vi.fn()}));
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

    it('默认按基准净值展示涨跌幅，并可切换为净值', async () => {
        useFundIntraday.mockReturnValue({data: {
            estimateDate: '2026-07-24', baseNav: '1.0000',
            points: [{time: '09:30', nav: '1.0010'}, {time: '09:31', nav: '1.0020'}],
        }, isLoading: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<FundIntradayChart fundId={1}/>));
        expect(chart.setStyles).toHaveBeenLastCalledWith({yAxis: {type: 'percentage'}});
        expect(chart.setScrollEnabled).toHaveBeenLastCalledWith(false);
        expect(chart.setZoomEnabled).toHaveBeenLastCalledWith(false);
        expect(chart.applyNewData).toHaveBeenLastCalledWith(expect.arrayContaining([
            expect.objectContaining({close: 1}), expect.objectContaining({close: 1.002, value: 1.002}),
        ]));

        const navOption = [...container.querySelectorAll('label')].find((label) => label.textContent.includes('净值'));
        await act(async () => navOption.click());
        expect(chart.setStyles).toHaveBeenLastCalledWith({yAxis: {type: 'normal'}});
        expect(chart.setScrollEnabled).toHaveBeenLastCalledWith(true);
        expect(chart.setZoomEnabled).toHaveBeenLastCalledWith(true);
        expect(chart.applyNewData).toHaveBeenLastCalledWith([
            expect.objectContaining({close: 1.001}), expect.objectContaining({close: 1.002}),
        ]);
    });

    it('按交易段补齐收盘时间，午休不占槽位，未来槽位保持空白', async () => {
        useFundIntraday.mockReturnValue({data: {
            estimateDate: '2026-07-24', baseNav: '1.0000',
            tradingSessions: [{start: '09:30', end: '11:30'}, {start: '13:00', end: '15:00'}],
            points: [
                {time: '09:30', nav: '1.0010'},
                {time: '11:30', nav: '1.0020'},
                {time: '13:00', nav: '1.0030'},
                {time: '13:01', nav: '1.0040'},
            ],
        }, isLoading: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<FundIntradayChart portfolioFundId={1}/>));

        const data = chart.applyNewData.mock.lastCall[0];
        const timestamp = (time) => new Date(`2026-07-24T${time}:00+08:00`).getTime();
        expect(data).toHaveLength(242);
        expect(data[0]).toMatchObject({timestamp: timestamp('09:30'), close: 1, value: 1.001});
        expect(data[120].timestamp).toBe(timestamp('11:30'));
        expect(data[121].timestamp).toBe(timestamp('13:00'));
        expect(data[122]).toMatchObject({timestamp: timestamp('13:01'), close: 1.004, value: 1.004});
        expect(data.at(-1)).toEqual({timestamp: timestamp('15:00')});
        expect(chart.setBarSpace).toHaveBeenLastCalledWith((1000 - 48) / 242);
        expect(chart.setOffsetRightDistance).toHaveBeenLastCalledWith(0);
        expect(container.querySelector('.intraday-chart-container').style.width).toBe('max(100%, 290px)');

        chart.getSize.mockReturnValue({width: 1500});
        await act(async () => window.dispatchEvent(new Event('resize')));
        expect(chart.setBarSpace).toHaveBeenLastCalledWith((1500 - 48) / 242);
    });
});
