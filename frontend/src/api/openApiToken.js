import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {del, get, post} from './client.js';

/** 当前用户 Open API Token 状态；明文从不进入查询缓存。 */
export function useOpenApiToken() {
    return useQuery({
        queryKey: ['open-api-token'],
        queryFn: () => get('/api/open-api/token'),
    });
}

/**
 * 创建或轮换 Token；返回中的 token 只应展示给用户一次。
 * POST 是变更操作，显式携带幂等键，防止浏览器/代理重试导致连续轮换。
 */
export function useRotateOpenApiToken() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: () => post('/api/open-api/token', undefined, {
            headers: {'Idempotency-Key': crypto.randomUUID()},
        }),
        onSuccess: () => qc.invalidateQueries({queryKey: ['open-api-token']}),
    });
}

export function useRevokeOpenApiToken() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: () => del('/api/open-api/token'),
        onSuccess: () => qc.invalidateQueries({queryKey: ['open-api-token']}),
    });
}
