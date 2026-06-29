// 真实后端 fetch 封装：解包 ApiResponse，失败抛含 code/message 的 Error。

/**
 * 调后端接口，返回 ApiResponse.data（已解包）。
 * @param {string} path 形如 /api/funds
 * @param {{method?: string, body?: any}} options
 * @returns {Promise<any>} data 字段
 */
export async function apiFetch(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const init = {method, headers: {}};
    if (options.body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(options.body);
    }
    let resp;
    try {
        resp = await fetch(path, init);
    } catch (e) {
        throw new ApiError('NETWORK_ERROR', `网络异常：${e.message}`);
    }
    let payload;
    try {
        payload = await resp.json();
    } catch {
        throw new ApiError('BAD_RESPONSE', `响应解析失败 (HTTP ${resp.status})`);
    }
    if (!resp.ok || payload?.success === false) {
        const code = payload?.code || `HTTP_${resp.status}`;
        const message = payload?.message || `请求失败 (HTTP ${resp.status})`;
        throw new ApiError(code, message);
    }
    return payload.data;
}

export class ApiError extends Error {
    constructor(code, message) {
        super(message);
        this.code = code;
    }
}

// 便捷方法
export const get = (path) => apiFetch(path);
export const post = (path, body) => apiFetch(path, {method: 'POST', body});
export const put = (path, body) => apiFetch(path, {method: 'PUT', body});
export const del = (path) => apiFetch(path, {method: 'DELETE'});
