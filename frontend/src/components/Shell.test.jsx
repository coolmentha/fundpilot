import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {expect, it, vi} from 'vitest';

vi.mock('../api/hooks.js', () => ({usePendingTransactions: () => ({data: []})}));
vi.mock('../auth/SiteAuthContext.js', () => ({
    useSiteAuth: () => ({user: {role: 'USER'}, logout: vi.fn()}),
}));
vi.mock('antd', async () => {
    const React = await import('react');
    const Box = ({children}) => React.createElement('div', null, children);
    Box.Header = Box;
    Box.Content = Box;
    Box.Sider = Box;
    return {
        Badge: Box,
        Button: ({children, ...props}) => React.createElement('button', props, children),
        Drawer: Box,
        Layout: Box,
        Menu: ({items = [], onClick}) => React.createElement('nav', null,
            items.flatMap((group) => (group.children || []).map((item) => React.createElement(
                'button', {key: item.key, onClick: () => onClick({key: item.key})}, item.label)))),
        Tooltip: Box,
    };
});

globalThis.React = React;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

it('opens cumulative returns from the navigation', async () => {
    const {default: Shell} = await import('./Shell.jsx');
    const container = document.createElement('div');
    document.body.appendChild(container);
    const root = createRoot(container);
    await act(async () => root.render(
        <MemoryRouter initialEntries={['/']}>
            <Routes>
                <Route element={<Shell/>}>
                    <Route index element={<div>home</div>}/>
                    <Route path="/returns" element={<div>returns-view</div>}/>
                </Route>
            </Routes>
        </MemoryRouter>,
    ));

    const link = [...container.querySelectorAll('button')]
        .find((button) => button.textContent === '累计收益');
    await act(async () => link.click());

    expect(container.textContent).toContain('returns-view');
    await act(async () => root.unmount());
    container.remove();
});
