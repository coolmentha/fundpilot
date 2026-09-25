import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {MemoryRouter} from 'react-router-dom';

const api = vi.hoisted(() => ({
    useFunds: vi.fn(),
    usePortfolioSummary: vi.fn(),
}));

vi.mock('../api/hooks.js', () => api);
vi.mock('../components/StatusTag.jsx', () => ({default: ({value}) => String(value ?? '')}));
vi.mock('../components/EmptyState.jsx', () => ({default: ({description}) => description}));
vi.mock('antd', async () => {
    const React = await import('react');
    const PassThrough = ({children}) => React.createElement('div', null, children);
    return {
        Card: ({children, extra}) => React.createElement('section', null, children, extra),
        Col: PassThrough,
        Row: PassThrough,
        Space: PassThrough,
        Statistic: ({title, value, formatter}) => React.createElement('div', null, title, formatter ? formatter(value) : value),
        Table: ({dataSource = [], columns, rowKey = 'key'}) => React.createElement('div', null,
            dataSource.flatMap((row) => columns.map((column, index) => React.createElement('div', {
                key: `${typeof rowKey === 'function' ? rowKey(row) : row[rowKey]}-${index}`,
                'data-row-key': typeof rowKey === 'function' ? rowKey(row) : row[rowKey],
            },
                column.render ? column.render(undefined, row) : row[column.dataIndex])))),
        Typography: {Title: ({children}) => React.createElement('h4', null, children)},
        Skeleton: () => null,
    };
});

import DashboardPage from './DashboardPage.jsx';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;

describe('DashboardPage', () => {
    let container;
    let root;

    beforeEach(() => {
        api.useFunds.mockReturnValue({data: [{id: 7, portfolioFundId: 71, fundName: '示例基金', status: 'HOLDING'}], isLoading: false});
        api.usePortfolioSummary.mockReturnValue({data: {}, isLoading: false});
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
    });

    it('持仓基金列表以组合基金标识区分多个无旧标识记录', async () => {
        api.useFunds.mockReturnValue({
            data: [
                {id: null, portfolioFundId: 71, fundName: '基金甲', status: 'HOLDING'},
                {id: null, portfolioFundId: 72, fundName: '基金乙', status: 'HOLDING'},
            ],
            isLoading: false,
        });
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter><DashboardPage/></MemoryRouter>));

        const keys = [...container.querySelectorAll('[data-row-key]')]
            .map((row) => row.getAttribute('data-row-key'));
        expect(keys).toContain('71');
        expect(keys).toContain('72');
    });
});
