import {useQuery, useMutation, useQueryClient} from '@tanstack/react-query';
import {get, post, put, del} from './client.js';

const realtimeQueryOptions = {
    refetchInterval: 30000,
    refetchIntervalInBackground: true,
    refetchOnWindowFocus: true,
};

// ===== 基金 =====
export function useFunds() {
    return useQuery({queryKey: ['funds'], queryFn: () => get('/api/funds'), ...realtimeQueryOptions});
}

export function useFund(id) {
    return useQuery({
        queryKey: ['funds', id],
        queryFn: () => get(`/api/funds/${id}`),
        enabled: !!id,
        ...realtimeQueryOptions,
    });
}

/** 基金字典搜索(ADR-0005):搜索框自动补全候选列表。 */
export function useFundSearch(query) {
    return useQuery({
        queryKey: ['fund-search', query],
        queryFn: () => get(`/api/funds/search?q=${encodeURIComponent(query)}`),
        enabled: !!query && query.trim().length > 0,
    });
}

export function useSaveFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({id, body}) => id ? put(`/api/funds/${id}`, body) : post('/api/funds', body),
        onSuccess: () => qc.invalidateQueries({queryKey: ['funds']}),
    });
}

export function useArchiveFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (id) => del(`/api/funds/${id}`),
        onSuccess: () => qc.invalidateQueries({queryKey: ['funds']}),
    });
}
export function useCreateFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => post('/api/funds', body),
        onSuccess: () => qc.invalidateQueries({queryKey: ['funds']}),
    });
}
export function useUpdateFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({id, body}) => put(`/api/funds/${id}`, body),
        onSuccess: () => qc.invalidateQueries({queryKey: ['funds']}),
    });
}

// ===== 策略 =====
export function useStrategies(fundId) {
    return useQuery({
        queryKey: ['strategies', fundId],
        queryFn: () => get(`/api/funds/${fundId}/strategies`),
        enabled: !!fundId,
    });
}
export function useActiveStrategy(fundId) {
    return useQuery({
        queryKey: ['strategy-active', fundId],
        queryFn: () => get(`/api/funds/${fundId}/strategies/active`),
        enabled: !!fundId,
    });
}
export function useStrategyRecommendation(fundId) {
    return useQuery({
        queryKey: ['strategy-recommendation', fundId],
        queryFn: () => get(`/api/funds/${fundId}/strategies/recommendation`),
        enabled: !!fundId,
    });
}
const useInvalidateStrategies = (fundId) => {
    const qc = useQueryClient();
    return () => {
        qc.invalidateQueries({queryKey: ['strategies', fundId]});
        qc.invalidateQueries({queryKey: ['strategy-active', fundId]});
    };
};
export function useCreateStrategy(fundId) {
    const onSuccess = useInvalidateStrategies(fundId);
    return useMutation({mutationFn: (body) => post(`/api/funds/${fundId}/strategies`, body), onSuccess});
}
export function useUpdateStrategy(fundId) {
    const onSuccess = useInvalidateStrategies(fundId);
    return useMutation({
        mutationFn: ({id, body}) => put(`/api/strategies/${id}`, body),
        onSuccess,
    });
}
export function useStrategyAction(fundId) {
    const onSuccess = useInvalidateStrategies(fundId);
    return useMutation({
        mutationFn: ({id, action}) => post(`/api/strategies/${id}/${action}`),
        onSuccess,
    });
}

// ===== 定投计划 =====
export function useDcaPlans(fundId) {
    return useQuery({
        queryKey: ['dca-plans', fundId],
        queryFn: () => get(`/api/funds/${fundId}/dca-plans`),
        enabled: !!fundId,
    });
}
export function useActiveDcaPlan(fundId) {
    return useQuery({
        queryKey: ['dca-active', fundId],
        queryFn: () => get(`/api/funds/${fundId}/dca-plans/active`),
        enabled: !!fundId,
    });
}
const useInvalidateDcaPlans = (fundId) => {
    const qc = useQueryClient();
    return () => {
        qc.invalidateQueries({queryKey: ['dca-plans', fundId]});
        qc.invalidateQueries({queryKey: ['dca-active', fundId]});
    };
};
export function useCreateDcaPlan(fundId) {
    const onSuccess = useInvalidateDcaPlans(fundId);
    return useMutation({mutationFn: (body) => post(`/api/funds/${fundId}/dca-plans`, body), onSuccess});
}
export function useUpdateDcaPlan(fundId) {
    const onSuccess = useInvalidateDcaPlans(fundId);
    return useMutation({
        mutationFn: ({id, body}) => put(`/api/dca-plans/${id}`, body),
        onSuccess,
    });
}
export function useDcaPlanAction(fundId) {
    const onSuccess = useInvalidateDcaPlans(fundId);
    return useMutation({
        mutationFn: ({id, action}) => post(`/api/dca-plans/${id}/${action}`),
        onSuccess,
    });
}

// ===== 信号 =====
export function useSignalsToday(fundId) {
    return useQuery({
        queryKey: ['signals-today', fundId],
        queryFn: () => get(`/api/funds/${fundId}/signals/today`),
        enabled: !!fundId,
        ...realtimeQueryOptions,
    });
}
export function useSignalsRange(fundId, from, to) {
    return useQuery({
        queryKey: ['signals-range', fundId, from, to],
        queryFn: () => get(`/api/funds/${fundId}/signals?from=${from}&to=${to}`),
        enabled: !!fundId && !!from && !!to,
    });
}
export function usePendingSignals() {
    return useQuery({queryKey: ['signals-pending'], queryFn: () => get('/api/signals/pending'), ...realtimeQueryOptions});
}
export function usePortfolioSummary() {
    return useQuery({queryKey: ['portfolio-summary'], queryFn: () => get('/api/portfolio/summary'), ...realtimeQueryOptions});
}
export function invalidateSignalQueries(queryClient) {
    queryClient.invalidateQueries({queryKey: ['signals-pending']});
    queryClient.invalidateQueries({queryKey: ['signals-today']});
    queryClient.invalidateQueries({queryKey: ['signals-range']});
}
export function useConfirmOperation(fundId) {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => post(`/api/funds/${fundId}/operations`, body),
        onSuccess: () => invalidateSignalQueries(qc),
    });
}

export function useIgnoreSignal() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({fundId, signalId}) => post(`/api/funds/${fundId}/signals/${signalId}/ignore`),
        onSuccess: () => invalidateSignalQueries(qc),
    });
}

// ===== 交易 =====
export function useFundTransactions(fundId) {
    return useQuery({
        queryKey: ['fund-transactions', fundId],
        queryFn: () => get(`/api/funds/${fundId}/transactions`),
        enabled: !!fundId,
    });
}
export function useFundFeeRates(fundId) {
    return useQuery({
        queryKey: ['fund-fee-rates', fundId],
        queryFn: () => get(`/api/funds/${fundId}/fee-rates`),
        enabled: !!fundId,
    });
}
export function useCancelTransaction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (id) => post(`/api/transactions/${id}/cancel`),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['fund-transactions']});
            qc.invalidateQueries({queryKey: ['funds']});
        },
    });
}
export function useConfirmTransaction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (id) => post(`/api/transactions/${id}/confirm`),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['fund-transactions']});
            qc.invalidateQueries({queryKey: ['funds']});
        },
    });
}
export function useCreateManualTransaction(fundId) {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => post(`/api/funds/${fundId}/transactions`, body),
        onSuccess: () => {
            // 转换模式会在另一只基金建转入腿,需刷新全部基金摘要;非转换也刷新当前基金流水
            qc.invalidateQueries({queryKey: ['fund-transactions', fundId]});
            qc.invalidateQueries({queryKey: ['funds']});
        },
    });
}

// ===== 用户配置 =====
export function useUserConfig() {
    return useQuery({queryKey: ['user-config'], queryFn: () => get('/api/user-config')});
}
export function useUpdateUserConfig() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => put('/api/user-config', body),
        // 配置更新后既刷配置页,也强制失效指数行情查询——后端已发事件即时刷缓存,
        // 前端必须重取才能立刻看到新关注指数,否则要等下一轮 5s 轮询。
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['user-config']});
            qc.invalidateQueries({queryKey: ['market', 'indices']});
        },
    });
}

// ===== 行情 =====
export function useMarketIndicatorsToday(fundId) {
    return useQuery({
        queryKey: ['market-today', fundId],
        queryFn: () => get(`/api/funds/${fundId}/market-indicators/today`),
        enabled: !!fundId,
    });
}

// ===== 管理 =====
const ADMIN_ACTION_PATHS = {
    generate: '/api/admin/signals/generate',
    'confirm-nav': '/api/admin/transactions/confirm-nav',
    'sync-dict': '/api/admin/fund-dict/sync',
    'sync-calendar': '/api/admin/market-data/sync-trading-calendar',
    refresh: '/api/admin/market-data/refresh',
};

export function requestAdminAction(action, adminKey) {
    const path = ADMIN_ACTION_PATHS[action];
    if (!path) throw new Error(`Unsupported admin action: ${action}`);
    return post(path, undefined, {headers: {'X-Admin-Key': adminKey}});
}

export function useAdminAction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({action, adminKey}) => requestAdminAction(action, adminKey),
        onSuccess: () => qc.invalidateQueries(),
    });
}

// ===== 行情实时(行情工作台) =====
// 前端高频轮询后端内存缓存,不直接击穿到东方财富。
// 交易时段内刷新;react-query refetchInterval 常驻轮询,非交易时段数据不变也无副作用。

/** 用户关注指数的实时行情,5 秒轮询。 */
export function useRealtimeIndices() {
    return useQuery({
        queryKey: ['market', 'indices'],
        queryFn: () => get('/api/market/indices/realtime'),
        refetchInterval: 5_000,
        refetchIntervalInBackground: false,
    });
}

/** 沪深京上涨、下跌股票家数,5 秒轮询。 */
export function useMarketBreadth() {
    return useQuery({
        queryKey: ['market', 'breadth'],
        queryFn: () => get('/api/market/breadth'),
        refetchInterval: 5_000,
        refetchIntervalInBackground: false,
    });
}

/** 批量基金盘中估值,10 秒轮询。codes 为空时不启用。 */
export function useFundEstimates(codes) {
    const codeStr = (codes || []).filter(Boolean).join(',');
    return useQuery({
        queryKey: ['market', 'estimates', codeStr],
        queryFn: () => get(`/api/market/funds/estimates?codes=${encodeURIComponent(codeStr)}`),
        enabled: !!codeStr,
        refetchInterval: 10_000,
        refetchIntervalInBackground: false,
    });
}

/** 行业板块涨跌排行,30 秒轮询。 */
export function useSectorPerformance() {
    return useQuery({
        queryKey: ['market', 'sectors'],
        queryFn: () => get('/api/market/sectors'),
        refetchInterval: 30_000,
        refetchIntervalInBackground: false,
    });
}

/** 北向资金净流入,30 秒轮询。 */
export function useMoneyFlow() {
    return useQuery({
        queryKey: ['market', 'money-flow'],
        queryFn: () => get('/api/market/money-flow'),
        refetchInterval: 30_000,
        refetchIntervalInBackground: false,
    });
}

/**
 * A 股交易时段是否开市(北京时间 工作日 9:30-11:30 / 13:00-15:00)。
 * <p>仅判断周末,法定节假日(春节/国庆等)未配表 —— 节假日轮询只会多发几次请求,
 * 后端 K 线接口有缓存兜底,代价可接受。用 UTC 计算 +8 偏移避免依赖运行环境时区。
 */
function isChinaMarketOpen(now = new Date()) {
    const utcMin = now.getUTCHours() * 60 + now.getUTCMinutes();
    const bjMin = (utcMin + 8 * 60) % 1440;
    const bjDay = (now.getUTCDay() + Math.floor((utcMin + 8 * 60) / 1440)) % 7;
    if (bjDay === 0 || bjDay === 6) return false; // 周末休市
    return (bjMin >= 570 && bjMin <= 690) || (bjMin >= 780 && bjMin <= 900); // 9:30-11:30, 13:00-15:00
}

/** 基金 K 线/走势图数据。period: daily/weekly/monthly。盘中(A 股交易时段)每 30s 轮询刷新,非交易时段不轮询。 */
export function useFundKline(fundId, period = 'daily') {
    return useQuery({
        queryKey: ['funds', fundId, 'kline', period],
        queryFn: () => get(`/api/funds/${fundId}/kline?period=${period}`),
        enabled: !!fundId,
        // 函数式 refetchInterval:每次轮询后重新求值。交易时段 30s 刷一次,过 15:00 自动停。
        // react-query structuralSharing 保证数据不变时引用相等,KlineChart effect 不触发、图表不重绘。
        refetchInterval: () => isChinaMarketOpen() ? 30000 : false,
    });
}
