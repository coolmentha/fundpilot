import {useQuery, useMutation, useQueryClient} from '@tanstack/react-query';
import {get, post, put, del} from './client.js';

const realtimeQueryOptions = {
    refetchInterval: 30000,
    refetchIntervalInBackground: true,
    refetchOnWindowFocus: true,
};

// ===== 基金 =====
export function useFunds() {
    return useQuery({queryKey: ['funds'], queryFn: () => get('/api/insights/portfolio/funds/current'), ...realtimeQueryOptions});
}

export function useFundGroups() {
    return useQuery({queryKey: ['fund-groups'], queryFn: () => get('/api/fund-groups')});
}

export function useSaveFundGroups() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => put('/api/fund-groups', body),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['fund-groups']});
            qc.invalidateQueries({queryKey: ['funds']});
        },
    });
}

export function useFund(id) {
    return useQuery({
        queryKey: ['funds', id],
        queryFn: () => get(`/api/insights/portfolio/funds/${id}`),
        enabled: !!id,
        ...realtimeQueryOptions,
    });
}

/** 产品目录搜索:新建组合基金时提供自动补全候选。 */
export function useFundSearch(query) {
    return useQuery({
        queryKey: ['fund-search', query],
        queryFn: () => get(`/api/products?q=${encodeURIComponent(query)}`),
        enabled: !!query && query.trim().length > 0,
    });
}

export function useSaveFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({id, body}) => id ? put(`/api/funds/${id}`, body) : post('/api/funds', body),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['funds']});
            qc.invalidateQueries({queryKey: ['fund-groups']});
        },
    });
}

export function useVoidPortfolioFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: voidPortfolioFund,
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['funds']});
            qc.invalidateQueries({queryKey: ['fund-transactions']});
            qc.invalidateQueries({queryKey: ['transactions-pending']});
            qc.invalidateQueries({queryKey: ['signals-pending']});
            qc.invalidateQueries({queryKey: ['dca-plans']});
        },
    });
}

export function voidPortfolioFund({portfolioFundId, reason}) {
    return post(`/api/portfolio-funds/${portfolioFundId}/void`, {reason, confirmed: true});
}
export function useCreateFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => post('/api/funds', body),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['funds']});
            qc.invalidateQueries({queryKey: ['fund-groups']});
        },
    });
}
export function useUpdateFund() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({id, body}) => put(`/api/funds/${id}`, body),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['funds']});
            qc.invalidateQueries({queryKey: ['fund-groups']});
        },
    });
}

// ===== 策略 =====
export function useStrategies(portfolioFundId) {
    return useQuery({
        queryKey: ['strategies', portfolioFundId],
        queryFn: () => get(`/api/discipline/strategies/portfolio-funds/${portfolioFundId}`),
        enabled: !!portfolioFundId,
    });
}
export function useActiveStrategy(portfolioFundId) {
    return useQuery({
        queryKey: ['strategy-active', portfolioFundId],
        queryFn: () => get(`/api/discipline/strategies/portfolio-funds/${portfolioFundId}/active`),
        enabled: !!portfolioFundId,
    });
}
export function useStrategyRecommendation(portfolioFundId) {
    return useQuery({
        queryKey: ['strategy-recommendation', portfolioFundId],
        queryFn: () => get(`/api/discipline/strategies/portfolio-funds/${portfolioFundId}/recommendation`),
        enabled: !!portfolioFundId,
    });
}
const useInvalidateStrategies = (fundId) => {
    const qc = useQueryClient();
    return () => {
        qc.invalidateQueries({queryKey: ['strategies', fundId]});
        qc.invalidateQueries({queryKey: ['strategy-active', fundId]});
    };
};
export function useCreateStrategy(portfolioFundId) {
    const onSuccess = useInvalidateStrategies(portfolioFundId);
    return useMutation({mutationFn: (body) => post(`/api/discipline/strategies/portfolio-funds/${portfolioFundId}`, body), onSuccess});
}
export function useUpdateStrategy(fundId) {
    const onSuccess = useInvalidateStrategies(fundId);
    return useMutation({
        mutationFn: ({id, body}) => put(`/api/discipline/strategies/${id}`, body),
        onSuccess,
    });
}
export function useStrategyAction(fundId) {
    const onSuccess = useInvalidateStrategies(fundId);
    return useMutation({
        mutationFn: ({id, action}) => post(`/api/discipline/strategies/${id}/${action}`),
        onSuccess,
    });
}

// ===== 定投计划 =====
export function useDcaPlans(portfolioFundId) {
    return useQuery({
        queryKey: ['dca-plans', portfolioFundId],
        queryFn: () => get(`/api/investment-plans/portfolio-funds/${portfolioFundId}`),
        enabled: !!portfolioFundId,
    });
}
export function useDcaManagementPlans() {
    return useQuery({
        queryKey: ['dca-plans', 'all'],
        queryFn: () => get('/api/investment-plans'),
        ...realtimeQueryOptions,
    });
}
export function useActiveDcaPlan(portfolioFundId) {
    return useQuery({
        queryKey: ['dca-active', portfolioFundId],
        queryFn: () => get(`/api/investment-plans/portfolio-funds/${portfolioFundId}/active`),
        enabled: !!portfolioFundId,
    });
}
export function invalidateDcaPlanQueries(queryClient) {
    queryClient.invalidateQueries({queryKey: ['dca-plans']});
    queryClient.invalidateQueries({queryKey: ['dca-active']});
    invalidateDcaBudgetSummary(queryClient);
}
const useInvalidateDcaPlans = () => {
    const qc = useQueryClient();
    return () => invalidateDcaPlanQueries(qc);
};
export function useCreateDcaPlan(portfolioFundId) {
    const onSuccess = useInvalidateDcaPlans();
    return useMutation({mutationFn: (body) => post(`/api/investment-plans/portfolio-funds/${portfolioFundId}`, body), onSuccess});
}
export function useUpdateDcaPlan(fundId) {
    const onSuccess = useInvalidateDcaPlans(fundId);
    return useMutation({
        mutationFn: ({id, body}) => put(`/api/investment-plans/${id}`, body),
        onSuccess,
    });
}
export function useDcaPlanAction(fundId) {
    const onSuccess = useInvalidateDcaPlans(fundId);
    return useMutation({
        mutationFn: ({id, action}) => post(`/api/investment-plans/${id}/${action}`),
        onSuccess,
    });
}
export function deleteDcaPlan(id) {
    return del(`/api/investment-plans/${id}`);
}
export function useDeleteDcaPlan() {
    const onSuccess = useInvalidateDcaPlans();
    return useMutation({mutationFn: deleteDcaPlan, onSuccess});
}

export function useDcaBudgetSummary() {
    return useQuery({
        queryKey: ['dca-budget-summary'],
        queryFn: () => get('/api/investment-plan-budget/summary'),
        ...realtimeQueryOptions,
    });
}

export function useInvestmentPlanBudget() {
    return useQuery({queryKey: ['investment-plan-budget'], queryFn: () => get('/api/investment-plan-budget')});
}

export function useUpdateInvestmentPlanBudget() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (monthlyBudget) => put('/api/investment-plan-budget', {monthlyBudget}),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['investment-plan-budget']});
            invalidateDcaBudgetSummary(qc);
        },
    });
}

export function invalidateDcaBudgetSummary(queryClient) {
    queryClient.invalidateQueries({queryKey: ['dca-budget-summary']});
}

// ===== 信号 =====
export function useSignalsToday(fundId) {
    return useQuery({
        queryKey: ['signals-today', fundId],
        queryFn: () => get(`/api/discipline/advice/funds/${fundId}/latest`),
        enabled: !!fundId,
        ...realtimeQueryOptions,
    });
}
export function useSignalsRange(fundId, from, to) {
    return useQuery({
        queryKey: ['signals-range', fundId, from, to],
        queryFn: () => get(`/api/discipline/advice/funds/${fundId}?from=${from}&to=${to}`),
        enabled: !!fundId && !!from && !!to,
    });
}
export function usePendingSignals() {
    return useQuery({queryKey: ['signals-pending'], queryFn: () => get('/api/discipline/advice/pending'), ...realtimeQueryOptions});
}
export function usePortfolioSummary() {
    return useQuery({queryKey: ['portfolio-summary'], queryFn: () => get('/api/insights/portfolio/summary'), ...realtimeQueryOptions});
}
export function usePortfolioReturns() {
    return useQuery({queryKey: ['portfolio-returns'], queryFn: () => get('/api/insights/portfolio/returns'), ...realtimeQueryOptions});
}
export function usePortfolioReturnTrends(period, from, to) {
    const params = new URLSearchParams({period});
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    return useQuery({
        queryKey: ['portfolio-return-trends', period, from, to],
        queryFn: () => get(`/api/insights/portfolio/return-trends?${params}`),
    });
}
export function invalidateSignalQueries(queryClient) {
    queryClient.invalidateQueries({queryKey: ['signals-pending']});
    queryClient.invalidateQueries({queryKey: ['signals-today']});
    queryClient.invalidateQueries({queryKey: ['signals-range']});
}
export function invalidateConfirmOperationQueries(queryClient) {
    invalidateSignalQueries(queryClient);
    queryClient.invalidateQueries({queryKey: ['transactions-pending']});
    queryClient.invalidateQueries({queryKey: ['fund-transactions']});
    queryClient.invalidateQueries({queryKey: ['funds']});
}
export function useConfirmOperation(_fundId) {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => post(`/api/discipline/advice/${body.adviceId}/accept`, {
            amount: body.actualAmount, shares: body.actualShares, tradeDate: null,
        }),
        onSuccess: () => invalidateConfirmOperationQueries(qc),
    });
}

export function useIgnoreSignal() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({signalId}) => post(`/api/discipline/advice/${signalId}/ignore`),
        onSuccess: () => invalidateSignalQueries(qc),
    });
}

// ===== 交易 =====
export function useFundTransactions(portfolioFundId) {
    return useQuery({
        queryKey: ['fund-transactions', portfolioFundId],
        queryFn: () => get(`/api/portfolio-funds/${portfolioFundId}/transactions`),
        enabled: !!portfolioFundId,
    });
}
export function usePendingTransactions() {
    return useQuery({
        queryKey: ['transactions-pending'],
        queryFn: () => get('/api/transactions/pending'),
        ...realtimeQueryOptions,
    });
}
export function useFundFeeRates(fundCode) {
    return useQuery({
        queryKey: ['fund-fee-rates', fundCode],
        queryFn: () => getFundFeeRates(fundCode),
        enabled: !!fundCode,
    });
}
export function getFundFeeRates(fundCode) {
    return get(`/api/products/${encodeURIComponent(fundCode)}/fees`);
}
export function useCancelTransaction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (id) => post(`/api/transactions/${id}/cancel`),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['fund-transactions']});
            qc.invalidateQueries({queryKey: ['transactions-pending']});
            qc.invalidateQueries({queryKey: ['funds']});
            invalidateSignalQueries(qc);
            invalidateDcaBudgetSummary(qc);
        },
    });
}
export function useConfirmTransaction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (id) => post(`/api/transactions/${id}/confirm`),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['fund-transactions']});
            qc.invalidateQueries({queryKey: ['transactions-pending']});
            qc.invalidateQueries({queryKey: ['funds']});
            invalidateSignalQueries(qc);
            invalidateDcaBudgetSummary(qc);
        },
    });
}
export function useUpdateTransaction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({id, body}) => updatePendingTransaction(id, body),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['fund-transactions']});
            qc.invalidateQueries({queryKey: ['transactions-pending']});
            qc.invalidateQueries({queryKey: ['funds']});
            qc.invalidateQueries({queryKey: ['portfolio-summary']});
            invalidateDcaBudgetSummary(qc);
        },
    });
}
export function updatePendingTransaction(id, body) {
    return put(`/api/transactions/${id}`, body);
}
export function useCreateManualTransaction(portfolioFundId) {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => post(`/api/portfolio-funds/${portfolioFundId}/transactions`, body),
        onSuccess: (_data, body) => {
            // 转换模式会在另一只基金建转入腿,需刷新目标基金流水与全部基金摘要;非转换也刷新当前基金流水
            qc.invalidateQueries({queryKey: ['fund-transactions', portfolioFundId]});
            if (body?.targetPortfolioFundId) {
                qc.invalidateQueries({queryKey: ['fund-transactions', body.targetPortfolioFundId]});
            }
            qc.invalidateQueries({queryKey: ['funds']});
            invalidateDcaBudgetSummary(qc);
        },
    });
}

// ===== MarketData 关注指数 =====
export function useWatchedIndices() {
    return useQuery({
        queryKey: ['market-data', 'watched-indices'],
        queryFn: getWatchedIndices,
    });
}

export function useReplaceWatchedIndices() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: replaceWatchedIndices,
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['market-data', 'watched-indices']});
            qc.invalidateQueries({queryKey: ['market', 'indices']});
        },
    });
}

export function getWatchedIndices() {
    return get('/api/market-data/watched-indices');
}

export function replaceWatchedIndices(indexCodes) {
    return put('/api/market-data/watched-indices', {indexCodes});
}

// ===== 养基宝持仓导入 =====
export const createYangjibaoSession = () => post('/api/imports/yangjibao/sessions');
export const getYangjibaoSession = (id) => get(`/api/imports/yangjibao/sessions/${id}`);
export const getYangjibaoPreview = (id) => get(`/api/imports/yangjibao/sessions/${id}/preview`);
export const getYangjibaoImportStatus = (id) => get(`/api/imports/yangjibao/sessions/${id}/import`);
export const retryYangjibaoImport = (id) => post(`/api/imports/yangjibao/sessions/${id}/import/retry`);
export const cancelYangjibaoSession = (id) => del(`/api/imports/yangjibao/sessions/${id}`);

export function useRunYangjibaoImport() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({sessionId, items}) => post(`/api/imports/yangjibao/sessions/${sessionId}/import`, {items}),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['funds']});
            qc.invalidateQueries({queryKey: ['fund-transactions']});
            qc.invalidateQueries({queryKey: ['portfolio-summary']});
            qc.invalidateQueries({queryKey: ['portfolio-returns']});
        },
    });
}

// ===== 行情 =====
export function useMarketIndicatorsToday(portfolioFundId) {
    return useQuery({
        queryKey: ['market-today', portfolioFundId],
        queryFn: () => get(`/api/portfolio-funds/${portfolioFundId}/market-indicators/today`),
        enabled: !!portfolioFundId,
    });
}

// ===== 管理 =====
const ADMIN_ACTION_PATHS = {
    generate: '/api/admin/signals/generate',
    'confirm-nav': '/api/admin/transactions/confirm-nav',
    'sync-dict': '/api/admin/products/catalog/sync',
    'sync-calendar': '/api/admin/market-data/sync-trading-calendar',
    refresh: '/api/admin/market-data/refresh',
};

export function requestAdminAction(action) {
    const path = ADMIN_ACTION_PATHS[action];
    if (!path) throw new Error(`Unsupported admin action: ${action}`);
    return post(path, undefined, action === 'refresh' ? {timeoutMs: 120_000} : {});
}

export function useAdminAction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({action}) => requestAdminAction(action),
        onSuccess: () => qc.invalidateQueries(),
    });
}

export function useAdminUsers() {
    return useQuery({queryKey: ['admin-users'], queryFn: () => get('/api/admin/users')});
}

export function saveAdminUser(path, body) {
    return post(path, body);
}

export function useAdminUserMutation() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: ({path, body}) => saveAdminUser(path, body),
        onSuccess: () => qc.invalidateQueries({queryKey: ['admin-users']}),
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

/** A 股交易状态与工作台核心行情时效,30 秒轮询。 */
export function useMarketStatus() {
    return useQuery({
        queryKey: ['market', 'status'],
        queryFn: () => get('/api/market/status'),
        refetchInterval: 30_000,
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
export function useFundKline(portfolioFundId, period = 'daily') {
    return useQuery({
        queryKey: ['funds', portfolioFundId, 'kline', period],
        queryFn: () => get(`/api/portfolio-funds/${portfolioFundId}/kline?period=${period}`),
        enabled: !!portfolioFundId,
        // 函数式 refetchInterval:每次轮询后重新求值。交易时段 30s 刷一次,过 15:00 自动停。
        // react-query structuralSharing 保证数据不变时引用相等,KlineChart effect 不触发、图表不重绘。
        refetchInterval: () => isChinaMarketOpen() ? 30000 : false,
    });
}

/** 基金详情当日分时数据，交易时段每 30 秒读取后端缓存。 */
export function useFundIntraday(portfolioFundId) {
    return useQuery({
        queryKey: ['funds', portfolioFundId, 'intraday'],
        queryFn: () => get(`/api/portfolio-funds/${portfolioFundId}/intraday`),
        enabled: !!portfolioFundId,
        refetchInterval: () => isChinaMarketOpen() ? 30000 : false,
    });
}
