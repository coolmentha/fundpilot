import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it} from 'vitest';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;

const {default: DcaBudgetOverview} = await import('./DcaBudgetOverview.jsx');

const summary = {
    confirmedInvestedAmount: 1000,
    pendingInvestedAmount: 200,
    futureAmount: 600,
    minimumFutureAmount: 400,
    maximumFutureAmount: 800,
    monthlyBudget: 2000,
    futurePlans: [
        {planId: 7, portfolioFundId: 11, executionDate: '2026-09-22T00:00:00+08:00', amount: 300, maximumAmount: 400},
        {planId: 8, portfolioFundId: 12, executionDate: '2026-09-29T00:00:00+08:00', amount: 300, maximumAmount: 500},
    ],
};

async function renderOverview(data = summary) {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    await act(async () => root.render(
        <MemoryRouter><DcaBudgetOverview summary={data}/></MemoryRouter>,
    ));
    return {container, root};
}

describe('DcaBudgetOverview', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    it('未来逐项预测默认收起,只显示条数', async () => {
        ({container, root} = await renderOverview());

        expect(container.textContent).toContain('未来逐项预测（2 项）');
        expect(container.querySelector('.dca-budget-future-plans .ant-collapse-item-active')).toBeNull();
        expect(container.textContent).not.toContain('组合基金 #11');
        // 其他分块不受折叠影响
        expect(container.textContent).toContain('智能计划未来区间');
        expect(container.textContent).toContain('全月预计');
    });

    it('展开未来逐项预测后逐条显示执行日和金额', async () => {
        ({container, root} = await renderOverview());

        const header = container.querySelector('.dca-budget-future-plans .ant-collapse-header');
        await act(async () => header.click());

        expect(container.textContent).toContain('组合基金 #11');
        expect(container.textContent).toContain('组合基金 #12');
        expect(container.textContent).toContain('预计 ¥300.00 · 最大 ¥400.00');
        expect(container.textContent).toContain('2026-09-22');
    });

    it('没有未来计划时不渲染折叠分块', async () => {
        ({container, root} = await renderOverview({...summary, futurePlans: []}));

        expect(container.textContent).not.toContain('未来逐项预测');
    });
});
