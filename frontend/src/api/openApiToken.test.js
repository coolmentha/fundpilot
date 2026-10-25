import {describe, expect, it, vi} from 'vitest';

const {get, post, del, invalidateQueries} = vi.hoisted(() => ({
    get: vi.fn(), post: vi.fn(), del: vi.fn(), invalidateQueries: vi.fn(),
}));
vi.mock('./client.js', () => ({get, post, del}));
vi.mock('@tanstack/react-query', async () => {
    const actual = await vi.importActual('@tanstack/react-query');
    return {
        ...actual,
        useQuery: (options) => options,
        useMutation: (options) => options,
        useQueryClient: () => ({invalidateQueries}),
    };
});

const {useOpenApiToken, useRevokeOpenApiToken, useRotateOpenApiToken} = await import('./openApiToken.js');

describe('openApiToken hooks', () => {
    it('查询和轮换使用当前用户 Token 管理接口', async () => {
        const query = useOpenApiToken();
        const mutation = useRotateOpenApiToken();
        expect(query.queryKey).toEqual(['open-api-token']);
        await query.queryFn();
        expect(get).toHaveBeenCalledWith('/api/open-api/token');

        await mutation.mutationFn();
        expect(post).toHaveBeenCalledWith('/api/open-api/token', undefined,
            {headers: {'Idempotency-Key': expect.any(String)}});
        mutation.onSuccess();
        expect(invalidateQueries).toHaveBeenCalledWith({queryKey: ['open-api-token']});
    });

    it('撤销 Token 后刷新状态查询', async () => {
        const mutation = useRevokeOpenApiToken();
        await mutation.mutationFn();
        expect(del).toHaveBeenCalledWith('/api/open-api/token');
        mutation.onSuccess();
        expect(invalidateQueries).toHaveBeenCalledWith({queryKey: ['open-api-token']});
    });
});
