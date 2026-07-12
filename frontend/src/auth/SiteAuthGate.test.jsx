import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../pages/LoginPage.jsx', () => ({
    default: ({onLogin}) => React.createElement(
        'button',
        {onClick: () => onLogin('candidate-key')},
        'login',
    ),
}));

vi.mock('../api/client.js', () => ({
    clearSiteApiKey: vi.fn(),
    setSiteApiKey: vi.fn(),
    setSiteUnauthorizedHandler: vi.fn(),
    verifySiteApiKey: vi.fn(),
}));

import SiteAuthGate from './SiteAuthGate.jsx';
import {
    clearSiteApiKey,
    setSiteApiKey,
    setSiteUnauthorizedHandler,
    verifySiteApiKey,
} from '../api/client.js';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

describe('SiteAuthGate', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        container = null;
        root = null;
        vi.clearAllMocks();
    });

    async function renderGate() {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        const queryClient = new QueryClient();
        await act(async () => {
            root.render(
                <QueryClientProvider client={queryClient}>
                    <SiteAuthGate><div>private-content</div></SiteAuthGate>
                </QueryClientProvider>,
            );
        });
    }

    it('does not mount private content before the key is verified', async () => {
        await renderGate();

        expect(container.textContent).toBe('login');
        expect(container.textContent).not.toContain('private-content');
    });

    it('stores the verified key in memory and returns to login on unauthorized', async () => {
        verifySiteApiKey.mockResolvedValue({authenticated: true});
        await renderGate();

        await act(async () => container.querySelector('button').click());

        expect(verifySiteApiKey).toHaveBeenCalledWith('candidate-key');
        expect(setSiteApiKey).toHaveBeenCalledWith('candidate-key');
        expect(container.textContent).toContain('private-content');

        const unauthorizedHandler = setSiteUnauthorizedHandler.mock.calls
            .find(([handler]) => typeof handler === 'function')[0];
        await act(async () => unauthorizedHandler());

        expect(clearSiteApiKey).toHaveBeenCalled();
        expect(container.textContent).toBe('login');
    });
});
