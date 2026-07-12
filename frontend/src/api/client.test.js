import {afterEach, describe, expect, it, vi} from 'vitest';
import {
    apiFetch,
    loginSiteApiKey,
    markSiteAuthChanged,
    setSiteUnauthorizedHandler,
    verifySiteSession,
} from './client.js';

describe('apiFetch headers', () => {
    afterEach(() => {
        vi.useRealTimers();
        markSiteAuthChanged();
        setSiteUnauthorizedHandler(null);
        vi.unstubAllGlobals();
    });

    it('preserves caller headers without adding a JSON content type for an empty body', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({success: true, data: {status: 'ok'}}),
        });
        vi.stubGlobal('fetch', fetchMock);

        await apiFetch('/api/admin/test', {
            method: 'POST',
            headers: {'X-Admin-Key': 'test-admin-key'},
        });

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/test', expect.objectContaining({
            method: 'POST',
            headers: {'X-Admin-Key': 'test-admin-key'},
            signal: expect.any(AbortSignal),
            credentials: 'same-origin',
        }));
    });

    it('sends same-origin credentials with every API request', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({success: true, data: []}),
        });
        vi.stubGlobal('fetch', fetchMock);
        await apiFetch('/api/funds', {headers: {Accept: 'application/json'}});

        expect(fetchMock).toHaveBeenCalledWith('/api/funds', expect.objectContaining({
            method: 'GET',
            headers: {Accept: 'application/json'},
            signal: expect.any(AbortSignal),
            credentials: 'same-origin',
        }));
    });

    it('uses the raw key only for the login request', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({success: true, data: null}),
        });
        vi.stubGlobal('fetch', fetchMock);
        await loginSiteApiKey('candidate-key');
        await apiFetch('/api/funds');

        expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/login');
        expect(fetchMock.mock.calls[0][1].headers['X-Admin-Key']).toBe('candidate-key');
        expect(fetchMock.mock.calls[1][1].headers['X-Admin-Key']).toBeUndefined();
    });

    it('verifies the current cookie session without a raw key header', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({success: true, data: {authenticated: true}}),
        });
        vi.stubGlobal('fetch', fetchMock);

        await verifySiteSession();

        expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/verify');
        expect(fetchMock.mock.calls[0][1].headers['X-Admin-Key']).toBeUndefined();
    });

    it('notifies the auth gate when an authenticated request becomes unauthorized', async () => {
        const onUnauthorized = vi.fn();
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: false,
            status: 401,
            json: async () => ({success: false, code: 'ADMIN_UNAUTHORIZED', message: '访问凭据无效'}),
        }));
        setSiteUnauthorizedHandler(onUnauthorized);

        await expect(apiFetch('/api/funds')).rejects.toMatchObject({code: 'ADMIN_UNAUTHORIZED'});

        expect(onUnauthorized).toHaveBeenCalledOnce();
    });

    it('ignores a late unauthorized response from an older auth generation', async () => {
        let resolveOldRequest;
        const oldResponse = new Promise((resolve) => {
            resolveOldRequest = resolve;
        });
        vi.stubGlobal('fetch', vi.fn().mockReturnValue(oldResponse));
        const onUnauthorized = vi.fn();
        setSiteUnauthorizedHandler(onUnauthorized);
        const pending = apiFetch('/api/funds');

        markSiteAuthChanged();
        resolveOldRequest({
            ok: false,
            status: 401,
            json: async () => ({success: false, code: 'ADMIN_UNAUTHORIZED', message: '访问凭据无效'}),
        });

        await expect(pending).rejects.toMatchObject({code: 'ADMIN_UNAUTHORIZED'});
        expect(onUnauthorized).not.toHaveBeenCalled();
    });

    it('aborts a request after the shared timeout', async () => {
        vi.useFakeTimers();
        vi.stubGlobal('fetch', vi.fn((path, init) => new Promise((resolve, reject) => {
            init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
        })));

        const pending = apiFetch('/api/funds');
        const rejection = expect(pending).rejects.toMatchObject({code: 'REQUEST_TIMEOUT'});
        await vi.advanceTimersByTimeAsync(15000);

        await rejection;
        vi.useRealTimers();
    });
});
