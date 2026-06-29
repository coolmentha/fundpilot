import {useQuery, useMutation, useQueryClient} from '@tanstack/react-query';
import {get, post, put, del} from './client.js';

const invalidateFunds = (qc) => qc.invalidateQueries({queryKey: ['funds']});

// ===== 基金 =====
export function useFunds() {
    return useQuery({queryKey: ['funds'], queryFn: () => get('/api/funds')});
}

export function useFund(id) {
    return useQuery({queryKey: ['funds', id], queryFn: () => get(`/api/funds/${id}`), enabled: !!id});
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
export function useBacktests(strategyId) {
    return useQuery({
        queryKey: ['backtests', strategyId],
        queryFn: () => get(`/api/strategies/${strategyId}/backtests`),
        enabled: !!strategyId,
    });
}
const invalidateStrategies = (fundId) => {
    const qc = useQueryClient();
    return () => {
        qc.invalidateQueries({queryKey: ['strategies', fundId]});
        qc.invalidateQueries({queryKey: ['strategy-active', fundId]});
    };
};
export function useCreateStrategy(fundId) {
    const onSuccess = invalidateStrategies(fundId);
    return useMutation({mutationFn: (body) => post(`/api/funds/${fundId}/strategies`, body), onSuccess});
}
export function useUpdateStrategy(fundId) {
    const onSuccess = invalidateStrategies(fundId);
    return useMutation({
        mutationFn: ({id, body}) => put(`/api/strategies/${id}`, body),
        onSuccess,
    });
}
export function useStrategyAction(fundId) {
    const onSuccess = invalidateStrategies(fundId);
    return useMutation({
        mutationFn: ({id, action}) => post(`/api/strategies/${id}/${action}`),
        onSuccess,
    });
}
/** 自动寻优(issue #30):从默认基准网格搜索最优参数,样本外验证通过则落库草稿+calibrate。 */
export function useOptimizeStrategy(fundId) {
    const onSuccess = invalidateStrategies(fundId);
    return useMutation({
        mutationFn: () => post(`/api/funds/${fundId}/strategies/optimize`),
        onSuccess,
    });
}

// ===== 信号 =====
export function useSignalsToday(fundId) {
    return useQuery({
        queryKey: ['signals-today', fundId],
        queryFn: () => get(`/api/funds/${fundId}/signals/today`),
        enabled: !!fundId,
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
    return useQuery({queryKey: ['signals-pending'], queryFn: () => get('/api/signals/pending')});
}
export function usePortfolioSummary() {
    return useQuery({queryKey: ['portfolio-summary'], queryFn: () => get('/api/portfolio/summary')});
}
export function useConfirmOperation(fundId) {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (body) => post(`/api/funds/${fundId}/operations`, body),
        onSuccess: () => {
            qc.invalidateQueries({queryKey: ['signals-pending']});
            qc.invalidateQueries({queryKey: ['signals-today']});
        },
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
        onSuccess: () => qc.invalidateQueries({queryKey: ['fund-transactions', fundId]}),
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
        onSuccess: () => qc.invalidateQueries({queryKey: ['user-config']}),
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
export function useAdminAction() {
    const qc = useQueryClient();
    return useMutation({
        mutationFn: (action) => {
            const path = action === 'generate' ? '/api/admin/signals/generate'
                : action === 'confirm-nav' ? '/api/admin/transactions/confirm-nav'
                : action === 'sync-dict' ? '/api/admin/fund-dict/sync'
                : action === 'sync-calendar' ? '/api/admin/market-data/sync-trading-calendar'
                : '/api/admin/market-data/refresh';
            return post(path);
        },
        onSuccess: () => qc.invalidateQueries(),
    });
}
