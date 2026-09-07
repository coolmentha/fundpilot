import {useMutation, useQueryClient} from '@tanstack/react-query';
import {post} from './client.js';

export function usePortfolioMarketRefresh(portfolioFundId) {
    const client = useQueryClient();
    return useMutation({
        mutationFn: () => post(`/api/portfolio-funds/${portfolioFundId}/market-data/refresh`, undefined, {timeoutMs: 120_000}),
        onSuccess: () => Promise.all([
            client.invalidateQueries({queryKey: ['funds', portfolioFundId]}),
            client.invalidateQueries({queryKey: ['market-today', portfolioFundId]}),
        ]),
    });
}
