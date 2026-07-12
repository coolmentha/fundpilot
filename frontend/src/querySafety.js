export function isQueryDataReady({data, isLoading, isError}) {
    return data !== undefined && data !== null && !isLoading && !isError;
}

export function buildFundWatchlistRows(funds, estimates, {estimatesFetched, estimatesError}) {
    return (funds || []).map((fund) => {
        const estimate = estimatesError ? undefined : estimates?.[fund.fundCode];
        const estimateFetchFailed = estimatesError
            || !!fund.estimateFetchFailed
            || (estimatesFetched && fund.isEstimated && !estimate);
        return {
            key: fund.id,
            id: fund.id,
            fundCode: fund.fundCode,
            fundName: fund.fundName,
            fundSubType: fund.fundSubType,
            changePct: estimateFetchFailed
                ? null
                : (estimate?.estimatedChangePct ?? fund.dailyChangePct ?? null),
            isEstimated: !estimateFetchFailed && !!estimate,
            estimateFetchFailed,
            estimateTime: estimate?.estimateTime,
            holdingShares: fund.holdingShares,
            dailyPnl: fund.dailyPnl,
            status: fund.status,
        };
    });
}
