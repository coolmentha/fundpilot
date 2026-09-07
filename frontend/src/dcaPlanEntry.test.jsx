import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('./api/client.js', () => ({
    del: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
}));

import {post} from './api/client.js';
import {createDcaPlan} from './api/hooks.js';
import DcaPlanFormModal from './pages/DcaPlanFormModal.jsx';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
};
window.getComputedStyle = () => ({width: '0px', getPropertyValue: () => ''});

describe('DCA plan entry', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        vi.clearAllMocks();
    });

    it('submits the existing form fields', async () => {
        const onOk = vi.fn();
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<DcaPlanFormModal open onOk={onOk}/>));
        await act(async () => new Promise(resolve => window.setTimeout(resolve, 0)));

        const confirm = document.querySelector('.ant-modal-footer .ant-btn-primary');
        await act(async () => confirm.click());

        expect(onOk).toHaveBeenCalledWith(expect.objectContaining({
            enabled: true,
            amount: 1000,
            frequency: 'WEEKLY',
            dayOfWeek: 1,
            amountStrategy: 'FIXED',
            referenceIndexCode: null,
            movingAverageDays: null,
        }));
    });

    it('creates through the portfolio fund route without a legacy fund identifier', async () => {
        const body = {enabled: true, amount: 100, frequency: 'WEEKLY', dayOfWeek: 3};

        await createDcaPlan({portfolioFundId: 77, body});

        expect(post).toHaveBeenCalledWith('/api/investment-plans/portfolio-funds/77', body);
        expect(body).not.toHaveProperty('fundId');
    });
});
