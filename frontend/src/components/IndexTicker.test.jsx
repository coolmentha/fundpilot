import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';
import {MemoryRouter} from 'react-router-dom';

const {useRealtimeIndices} = vi.hoisted(() => ({useRealtimeIndices: vi.fn()}));
vi.mock('../api/hooks.js', () => ({useRealtimeIndices}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;

const {default: IndexTicker} = await import('./IndexTicker.jsx');

describe('IndexTicker', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.clearAllMocks();
    });

    it('优先展示成交额，没有成交额时回退涨跌额', async () => {
        useRealtimeIndices.mockReturnValue({
            data: [
                {secid: '1.000001', name: '上证指数', currentPrice: 3500, changePct: 0.01,
                    changeAmount: 35, turnover: 123456789},
                {secid: '100.NDX', name: '纳斯达克', currentPrice: 21000, changePct: -0.001,
                    changeAmount: -21, turnover: null},
            ],
            isLoading: false,
            isError: false,
        });
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<MemoryRouter><IndexTicker/></MemoryRouter>));

        expect(container.textContent).toContain('成交额 1.23亿');
        expect(container.textContent).toContain('涨跌额 -21.00');
    });
});
