import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';

const {useMarketVolumePrice} = vi.hoisted(() => ({useMarketVolumePrice: vi.fn()}));
vi.mock('../api/hooks.js', () => ({useMarketVolumePrice}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;

const {default: MarketVolumePrice} = await import('./MarketVolumePrice.jsx');

describe('MarketVolumePrice', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.clearAllMocks();
    });

    it('展示盘中放量上涨及纪律提醒', async () => {
        useMarketVolumePrice.mockReturnValue({
            data: {
                state: 'HIGH_UP', phase: 'INTRADAY_ESTIMATE', changePct: 0.01,
                volumeRatio: 1.68, quoteTime: '2026-07-10T05:30:00Z',
            },
            isLoading: false,
            isError: false,
        });

        await render();

        expect(container.textContent).toContain('放量上涨');
        expect(container.textContent).toContain('盘中暂估');
        expect(container.textContent).toContain('上证 +1.00%');
        expect(container.textContent).toContain('量比 1.68');
        expect(container.textContent).toContain('避免急涨追高');
        expect(container.textContent).toContain('2026-07-10 13:30:00');
        expect(container.querySelector('[aria-live="polite"]')).not.toBeNull();
    });

    it('数据不可用时不显示方向性纪律提醒', async () => {
        useMarketVolumePrice.mockReturnValue({
            data: {state: 'UNAVAILABLE', phase: 'CLOSED', quoteTime: null},
            isLoading: false,
            isError: false,
        });

        await render();

        expect(container.textContent).toContain('量能观察中');
        expect(container.textContent).toContain('暂不生成纪律提醒');
        expect(container.textContent).not.toContain('避免急涨追高');
        expect(container.textContent).toContain('行情时间待刷新');
    });

    it('有效状态缺少量价数值时不伪造零值', async () => {
        useMarketVolumePrice.mockReturnValue({
            data: {state: 'HIGH_UP', phase: 'INTRADAY_ESTIMATE', changePct: null, volumeRatio: null},
            isLoading: false,
            isError: false,
        });

        await render();

        expect(container.textContent).toContain('量能观察中');
        expect(container.textContent).not.toContain('0.00');
        expect(container.textContent).not.toContain('避免急涨追高');
    });

    async function render() {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MarketVolumePrice/>));
    }
});
