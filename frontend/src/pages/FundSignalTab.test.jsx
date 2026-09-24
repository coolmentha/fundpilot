import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const mocks = vi.hoisted(() => ({today: vi.fn(), range: vi.fn()}));

vi.mock('../api/hooks.js', () => ({
    useSignalsToday: mocks.today,
    useSignalsRange: mocks.range,
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
globalThis.ResizeObserver = class {
    observe() {
    }

    unobserve() {
    }

    disconnect() {
    }
};
const originalGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = (element) => originalGetComputedStyle.call(window, element);

const {default: FundSignalTab} = await import('./FundSignalTab.jsx');

describe('FundSignalTab', () => {
    let container;
    let root;

    beforeEach(() => {
        mocks.today.mockReset();
        mocks.range.mockReset();
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    const render = async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<FundSignalTab portfolioFundId={41}/>));
    };

    it('按锁定基金请求今日与历史建议并渲染全部列', async () => {
        mocks.today.mockReturnValue({
            data: {
                id: 1, responseStatus: 'RESPONDED', action: 'ADD', triggerTier: 2, coefficient: 0.1234,
                reason: '估值处于低估区', suggestedMeasure: {value: 123.456, measureUnit: 'AMOUNT'},
                warnings: '净值数据滞后', signalDate: '2026-09-18T06:30:00Z',
            },
            isLoading: false, isError: false, refetch: vi.fn(),
        });
        mocks.range.mockReturnValue({
            data: [{
                id: 9, responseStatus: 'IGNORED', action: 'SELL', triggerTier: null, coefficient: null,
                reason: null, suggestedMeasure: null, warnings: null, signalDate: null,
            }],
            isLoading: false, isError: false, refetch: vi.fn(),
        });
        await render();

        expect(container.textContent).toContain('今日建议');
        expect(container.textContent).toContain('历史建议');
        expect(container.textContent).toContain('已回应');
        expect(container.textContent).toContain('加仓');
        expect(container.textContent).toContain('0.1234');
        expect(container.textContent).toContain('估值处于低估区');
        expect(container.textContent).toContain('123.46 (金额)');
        expect(container.textContent).toContain('净值数据滞后');
        expect(container.textContent).toContain('2026-09-18 14:30:00');
        // 历史行缺失的档位/系数/建议量回退为占位符
        expect(container.textContent).toContain('已忽略');
        expect(container.textContent).toContain('卖出');

        expect(mocks.today).toHaveBeenCalledWith(41);
        expect(mocks.range).toHaveBeenCalledWith(41, undefined, undefined);
    });

    it('加载失败时展示错误态并可按卡片重试', async () => {
        const refetchToday = vi.fn();
        const refetchRange = vi.fn();
        mocks.today.mockReturnValue({data: undefined, isLoading: false, isError: true, refetch: refetchToday});
        mocks.range.mockReturnValue({data: undefined, isLoading: false, isError: true, refetch: refetchRange});
        await render();

        expect(container.textContent).toContain('今日建议加载失败');
        expect(container.textContent).toContain('历史建议加载失败');
        const retries = [...container.querySelectorAll('.query-error-state button')];
        expect(retries).toHaveLength(2);

        await act(async () => retries[0].click());
        await act(async () => retries[1].click());

        expect(refetchToday).toHaveBeenCalledTimes(1);
        expect(refetchRange).toHaveBeenCalledTimes(1);
    });

    it('无建议时展示空态而非表格数据', async () => {
        mocks.today.mockReturnValue({data: null, isLoading: false, isError: false, refetch: vi.fn()});
        mocks.range.mockReturnValue({data: [], isLoading: false, isError: false, refetch: vi.fn()});
        await render();

        expect(container.textContent).toContain('今日无建议');
        expect(container.textContent).toContain('所选区间无建议');
        expect(container.querySelectorAll('.ant-table-tbody tr.ant-table-row')).toHaveLength(0);
    });
});