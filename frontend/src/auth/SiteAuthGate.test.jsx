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
    loginSiteApiKey: vi.fn(),
    logoutSiteSession: vi.fn(),
    markSiteAuthChanged: vi.fn(),
    setSiteUnauthorizedHandler: vi.fn(),
    verifySiteSession: vi.fn(),
}));

import SiteAuthGate from './SiteAuthGate.jsx';
import {
    loginSiteApiKey,
    logoutSiteSession,
    markSiteAuthChanged,
    setSiteUnauthorizedHandler,
    verifySiteSession,
} from '../api/client.js';
import {SITE_LOGOUT_EVENT_KEY} from './siteAuthStorage.js';
import {useSiteAuth} from './SiteAuthContext.js';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

function AuthProbe() {
    const {user, updateUser} = useSiteAuth();
    return React.createElement('div', null, `${user?.email}:${typeof updateUser}`);
}

class ProbeErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {message: ''};
    }

    static getDerivedStateFromError(error) {
        return {message: error.message};
    }

    render() {
        return this.state.message ? React.createElement('div', null, this.state.message) : this.props.children;
    }
}

describe('SiteAuthGate', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        container = null;
        root = null;
        localStorage.clear();
        vi.clearAllMocks();
    });

    async function renderGate(children = <div>private-content</div>) {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        const queryClient = new QueryClient();
        await act(async () => {
            root.render(
                <QueryClientProvider client={queryClient}>
                    <SiteAuthGate>{children}</SiteAuthGate>
                </QueryClientProvider>,
            );
        });
    }

    it('does not mount private content before the key is verified', async () => {
        verifySiteSession.mockReturnValue(new Promise(() => {}));
        await renderGate();

        expect(container.textContent).not.toContain('private-content');
        expect(container.textContent).not.toBe('login');
    });

    it('logs in with the raw key once and returns to login on unauthorized', async () => {
        verifySiteSession.mockRejectedValue({code: 'ADMIN_UNAUTHORIZED'});
        loginSiteApiKey.mockResolvedValue({authenticated: true});
        logoutSiteSession.mockResolvedValue(true);
        await renderGate();
        await act(async () => undefined);

        await act(async () => container.querySelector('button').click());

        expect(loginSiteApiKey).toHaveBeenCalledWith('candidate-key');
        expect(container.textContent).toContain('private-content');

        const unauthorizedHandler = setSiteUnauthorizedHandler.mock.calls
            .find(([handler]) => typeof handler === 'function')[0];
        await act(async () => unauthorizedHandler());

        expect(logoutSiteSession).not.toHaveBeenCalled();
        expect(markSiteAuthChanged).toHaveBeenCalled();
        expect(container.textContent).toBe('login');
    });

    it('restores a persistent cookie session only after it is verified', async () => {
        let resolveVerification;
        verifySiteSession.mockReturnValue(new Promise((resolve) => {
            resolveVerification = resolve;
        }));

        await renderGate();

        expect(verifySiteSession).toHaveBeenCalled();
        expect(container.textContent).not.toContain('private-content');
        expect(container.textContent).not.toBe('login');

        await act(async () => resolveVerification({authenticated: true}));

        expect(container.textContent).toContain('private-content');
    });

    it('returns to login when the persistent session is unauthorized', async () => {
        verifySiteSession.mockRejectedValue({code: 'ADMIN_UNAUTHORIZED'});

        await renderGate();
        await act(async () => undefined);

        expect(markSiteAuthChanged).toHaveBeenCalled();
        expect(container.textContent).toBe('login');
    });

    it('keeps the persistent session and offers retry when verification is temporarily unavailable', async () => {
        verifySiteSession.mockRejectedValueOnce({code: 'NETWORK_ERROR'});

        await renderGate();
        await act(async () => undefined);

        expect(logoutSiteSession).not.toHaveBeenCalled();
        expect([...container.querySelectorAll('button')]
            .some((button) => button.textContent.replace(/\s/g, '') === '重试')).toBe(true);
    });

    it('logs out when another tab broadcasts a logout event', async () => {
        verifySiteSession.mockResolvedValue({authenticated: true});
        await renderGate();
        await act(async () => undefined);
        expect(container.textContent).toContain('private-content');

        await act(async () => window.dispatchEvent(new StorageEvent('storage', {
            key: SITE_LOGOUT_EVENT_KEY,
            newValue: '1',
            storageArea: localStorage,
        })));

        expect(container.textContent).toBe('login');
    });

    it('exposes the signed-in user and updateUser to consumers', async () => {
        verifySiteSession.mockResolvedValue({authenticated: true, email: 'alice@example.com'});
        await renderGate(<AuthProbe/>);
        await act(async () => undefined);

        expect(container.textContent).toBe('alice@example.com:function');
    });

    it('rejects useSiteAuth outside the gate', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<ProbeErrorBoundary><AuthProbe/></ProbeErrorBoundary>));

        expect(container.textContent).toContain('useSiteAuth must be used inside SiteAuthGate');
    });
});
