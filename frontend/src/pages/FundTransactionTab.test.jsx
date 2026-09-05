import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const mocks = vi.hoisted(() => {
    const mutateAsync = vi.fn().mockResolvedValue({});
    const portfolioFundIds = [];
    return {
        mutateAsync,
        portfolioFundIds,
        useCreateManualTransaction: vi.fn((portfolioFundId) => {
            portfolioFundIds.push(portfolioFundId);
            return {mutateAsync, isPending: false};
        }),
    };
});

vi.mock('../api/hooks.js', () => ({
    useFundTransactions: () => ({data: [], isLoading: false}),
    useFunds: () => ({data: [
        {id: 1, portfolioFundId: 101, fundName: '来源基金', fundCode: 'SRC', holdingShares: 100},
        {id: 2, portfolioFundId: 202, fundName: '目标基金', fundCode: 'DST', holdingShares: 50},
    ]}),
    useCancelTransaction: () => ({mutate: vi.fn(), isPending: false}),
    useConfirmTransaction: () => ({mutate: vi.fn(), isPending: false}),
    useCreateManualTransaction: mocks.useCreateManualTransaction,
    useFundFeeRates: () => ({data: {redemptionLadder: []}}),
    useUpdateTransaction: () => ({mutateAsync: vi.fn(), isPending: false}),
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
window.getComputedStyle = () => ({width: '0px', getPropertyValue: () => ''});
Element.prototype.scrollIntoView = vi.fn();

const {default: FundTransactionTab} = await import('./FundTransactionTab.jsx');

async function setInputValue(input, value) {
    await act(async () => {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        setter.call(input, String(value));
        input.dispatchEvent(new Event('input', {bubbles: true}));
        input.dispatchEvent(new Event('change', {bubbles: true}));
    });
}

async function click(element) {
    await act(async () => {
        element.click();
        await Promise.resolve();
    });
}

async function openSelect(element) {
    await act(async () => {
        element.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
        await Promise.resolve();
    });
}

describe('FundTransactionTab', () => {
    let container;
    let root;

    beforeEach(() => {
        mocks.mutateAsync.mockClear();
        mocks.portfolioFundIds.length = 0;
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
    });

    async function renderTab() {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<FundTransactionTab fundId={1} portfolioFundId={101}/>));
        await click([...container.querySelectorAll('button')]
            .find((button) => button.textContent.includes('手动录入')));
        const modals = document.body.querySelectorAll('.ant-modal');
        return modals[modals.length - 1];
    }

    it('submits an ordinary transaction through the current portfolio fund route', async () => {
        const modal = await renderTab();
        await setInputValue(modal.querySelector('.ant-input-number-input'), 100);
        await click(modal.querySelector('.ant-modal-footer button:last-child'));

        expect(mocks.portfolioFundIds.at(-1)).toBe(101);
        expect(mocks.mutateAsync).toHaveBeenCalledTimes(1);
        const body = mocks.mutateAsync.mock.calls[0][0];
        expect(body).toEqual(expect.objectContaining({source: 'INCREASE', amount: 100}));
        expect(body).not.toHaveProperty('targetPortfolioFundId');
    });

    it('maps a transfer target to portfolioFundId and sends only the portfolio target field', async () => {
        const modal = await renderTab();
        await openSelect(modal.querySelector('.ant-select'));
        await click([...document.querySelectorAll('.ant-select-item-option')]
            .find((option) => option.textContent.trim() === '转出'));

        const selects = modal.querySelectorAll('.ant-select-content');
        await openSelect(selects[1].closest('.ant-select'));
        await click([...document.querySelectorAll('.ant-select-item-option')]
            .find((option) => option.textContent.includes('目标基金')));
        await setInputValue(modal.querySelector('.ant-input-number-input'), 12.5);
        await click(modal.querySelector('.ant-modal-footer button:last-child'));

        expect(mocks.portfolioFundIds.at(-1)).toBe(101);
        expect(mocks.mutateAsync).toHaveBeenCalledTimes(1);
        const body = mocks.mutateAsync.mock.calls[0][0];
        expect(body).toEqual({
            source: 'TRANSFER_OUT',
            shares: 12.5,
            targetPortfolioFundId: 202,
            tradeDate: expect.any(String),
        });
    });
});
