import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';

const {chart, useFundIntraday} = vi.hoisted(() => ({
    chart: {resize: vi.fn(), setStyles: vi.fn(), setScrollEnabled: vi.fn(), setZoomEnabled: vi.fn(), setPriceVolumePrecision: vi.fn(), applyNewData: vi.fn()},
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
            expect.objectContaining({close: 1}), expect.objectContaining({close: 1.001}), expect.objectContaining({close: 1.002}),
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
});
