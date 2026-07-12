import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiFetch} from './client.js';

describe('apiFetch headers', () => {
    afterEach(() => {
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
});
