// 真实后端 fetch 封装：解包 ApiResponse，失败抛含 code/message 的 Error。

let siteUnauthorizedHandler = null;
let siteAuthGeneration = 0;
const REQUEST_TIMEOUT_MS = 15000;

export function markSiteAuthChanged() {
    siteAuthGeneration += 1;
}

export function setSiteUnauthorizedHandler(handler) {
    siteUnauthorizedHandler = handler;
}

/**
 * 调后端接口，返回 ApiResponse.data（已解包）。
 * @param {string} path 形如 /api/funds
 * @param {{method?: string, body?: any, headers?: Record<string, string>}} options
 * @returns {Promise<any>} data 字段
 */
export async function apiFetch(path, options = {}) {
    const requestAuthGeneration = siteAuthGeneration;
    const method = (options.method || 'GET').toUpperCase();
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    const init = {
        method,
        headers: {...(options.headers || {})},
        signal: controller.signal,
        credentials: 'same-origin',
    };
    if (options.body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(options.body);
    }
    let resp;
    try {
        resp = await fetch(path, init);
    } catch (e) {
        if (e?.name === 'AbortError') {
            throw new ApiError('REQUEST_TIMEOUT', '请求超时，请稍后重试');
        }
        throw new ApiError('NETWORK_ERROR', `网络异常：${e.message}`);
    } finally {
        clearTimeout(timeoutId);
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
        if ((resp.status === 401 || code === 'ADMIN_UNAUTHORIZED')
            && requestAuthGeneration === siteAuthGeneration) {
            siteUnauthorizedHandler?.();
        }
        throw new ApiError(code, message);
    }
    return payload.data;
}

export function loginSiteApiKey(apiKey) {
    return apiFetch('/api/auth/login', {
        method: 'POST',
        headers: {'X-Admin-Key': apiKey},
    });
}

export function verifySiteSession() {
    return apiFetch('/api/auth/verify');
}

export function logoutSiteSession() {
    return apiFetch('/api/auth/logout', {method: 'POST'});
}

export class ApiError extends Error {
    constructor(code, message) {
        super(message);
        this.code = code;
    }
}

// 便捷方法
export const get = (path) => apiFetch(path);
export const post = (path, body, options = {}) => apiFetch(path, {...options, method: 'POST', body});
export const put = (path, body) => apiFetch(path, {method: 'PUT', body});
export const del = (path) => apiFetch(path, {method: 'DELETE'});
