import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {Link, MemoryRouter, Outlet, useParams} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('./components/Shell.jsx', () => ({
    default: () => <main><Outlet/></main>,
}));
vi.mock('./pages/MarketDashboardPage.jsx', () => ({default: () => <div>market-home</div>}));
vi.mock('./pages/DashboardPage.jsx', () => ({default: () => <div>dashboard</div>}));
vi.mock('./pages/FundsPage.jsx', () => ({
    default: () => <div>fund-list<Link to="/funds/42">open-fund</Link></div>,
}));
vi.mock('./pages/FundDetailPage.jsx', () => ({
    default: function FundDetailPageMock() {
        const {fundId} = useParams();
        return <div>fund-detail-{fundId}</div>;
    },
}));
vi.mock('./pages/DcaManagementPage.jsx', () => ({default: () => <div>dca</div>}));
vi.mock('./pages/SignalsPage.jsx', () => ({default: () => <div>signals</div>}));
vi.mock('./pages/ConfirmPage.jsx', () => ({default: () => <div>confirm</div>}));
vi.mock('./pages/SettingsPage.jsx', () => ({default: () => <div>settings</div>}));
vi.mock('./pages/AdminPage.jsx', () => ({default: () => <div>admin</div>}));

import App from './App.jsx';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

describe('App routes', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
    });

    async function renderAt(path) {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => {
            root.render(React.createElement(
                MemoryRouter,
                {initialEntries: [path]},
                React.createElement(App),
            ));
        });
    }

    it('navigates from the fund list to the selected fund detail', async () => {
        await renderAt('/funds');
        expect(container.textContent).toContain('fund-list');

        await act(async () => container.querySelector('a').click());

        expect(container.textContent).toContain('fund-detail-42');
    });

    it('redirects unknown routes to the market home', async () => {
        await renderAt('/unknown');

        expect(container.textContent).toContain('market-home');
    });
});
