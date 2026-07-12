import {afterEach, describe, expect, it, vi} from 'vitest';
import {
    apiFetch,
    clearSiteApiKey,
    setSiteApiKey,
    setSiteUnauthorizedHandler,
    verifySiteApiKey,
} from './client.js';

describe('apiFetch headers', () => {
    afterEach(() => {
        clearSiteApiKey();
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

        expect(fetchMock).toHaveBeenCalledWith('/api/admin/test', {
            method: 'POST',
            headers: {'X-Admin-Key': 'test-admin-key'},
        });
    });

    it('adds the in-memory site key to every API request', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({success: true, data: []}),
        });
        vi.stubGlobal('fetch', fetchMock);
        setSiteApiKey('site-key');

        await apiFetch('/api/funds', {headers: {Accept: 'application/json'}});

        expect(fetchMock).toHaveBeenCalledWith('/api/funds', {
            method: 'GET',
            headers: {Accept: 'application/json', 'X-Admin-Key': 'site-key'},
        });
    });

    it('does not allow a caller header to override the authenticated site key', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({success: true, data: null}),
        });
        vi.stubGlobal('fetch', fetchMock);
        setSiteApiKey('site-key');

        await apiFetch('/api/funds', {headers: {'X-Admin-Key': 'wrong-key'}});

        expect(fetchMock.mock.calls[0][1].headers['X-Admin-Key']).toBe('site-key');
    });

    it('verifies a candidate key without persisting it as the site key', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({success: true, data: {authenticated: true}}),
        });
        vi.stubGlobal('fetch', fetchMock);

        await verifySiteApiKey('candidate-key');
        await apiFetch('/api/funds');

        expect(fetchMock.mock.calls[0][1].headers['X-Admin-Key']).toBe('candidate-key');
        expect(fetchMock.mock.calls[1][1].headers['X-Admin-Key']).toBeUndefined();
    });

    it('notifies the auth gate when an authenticated request becomes unauthorized', async () => {
        const onUnauthorized = vi.fn();
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: false,
            status: 401,
            json: async () => ({success: false, code: 'ADMIN_UNAUTHORIZED', message: '访问凭据无效'}),
        }));
        setSiteApiKey('site-key');
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
        setSiteApiKey('old-key');
        setSiteUnauthorizedHandler(onUnauthorized);
        const pending = apiFetch('/api/funds');

        clearSiteApiKey();
        setSiteApiKey('new-key');
        resolveOldRequest({
            ok: false,
            status: 401,
            json: async () => ({success: false, code: 'ADMIN_UNAUTHORIZED', message: '访问凭据无效'}),
        });

        await expect(pending).rejects.toMatchObject({code: 'ADMIN_UNAUTHORIZED'});
        expect(onUnauthorized).not.toHaveBeenCalled();
    });
});
