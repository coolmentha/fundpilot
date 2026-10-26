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
 * 生成 UUID v4 幂等键。
 * randomUUID 仅在安全上下文可用；局域网 HTTP 部署下回退到 getRandomValues。
 */
function createIdempotencyKey() {
    const cryptoApi = globalThis.crypto;
    if (typeof cryptoApi?.randomUUID === 'function') {
        return cryptoApi.randomUUID();
    }
    if (typeof cryptoApi?.getRandomValues !== 'function') {
        throw new Error('当前浏览器不支持安全随机数生成');
    }

    const bytes = new Uint8Array(16);
    cryptoApi.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
    return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16),
        hex.slice(16, 20), hex.slice(20)].join('-');
}

/**
 * 创建或轮换 Token；返回中的 token 只应展示给用户一次。
 * POST 是变更操作，显式携带幂等键，防止浏览器/代理重试导致连续轮换。
 */
export function useRotateOpenApiToken() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: () => post('/api/open-api/token', undefined, {
            headers: {'Idempotency-Key': createIdempotencyKey()},
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
