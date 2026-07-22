export function isQueryDataReady({data, isLoading, isError}) {
    return data !== undefined && data !== null && !isLoading && !isError;
}

export function estimateStatusText(status) {
    if (status === 'TIMEOUT' || status === 'PARSE_ERROR') return '估值拉取失败';
    if (status === 'UNAVAILABLE' || status === 'STALE' || status === 'NOT_ATTEMPTED') return '暂无估值';
    return null;
}

export function buildFundWatchlistRows(funds, estimates, {estimatesFetched, estimatesError}) {
    return (funds || []).map((fund) => {
        const confirmedNav = fund.valuationSource === 'CONFIRMED_NAV'
            || (fund.investmentTarget === 'QDII'
                && fund.valuationSource === 'LATEST_CONFIRMED_NAV');
        const effectiveEstimatesError = estimatesError && !confirmedNav;
        const estimate = effectiveEstimatesError || confirmedNav ? undefined : estimates?.[fund.fundCode];
        const estimateStatus = effectiveEstimatesError
            ? 'TIMEOUT'
            : estimate
                ? 'AVAILABLE'
                : (estimatesFetched && fund.isEstimated && !estimate)
                    ? 'TIMEOUT'
                    : (fund.estimateStatus || (fund.estimateFetchFailed ? 'TIMEOUT' : 'NOT_ATTEMPTED'));
        const estimateFetchFailed = estimateStatus === 'TIMEOUT' || estimateStatus === 'PARSE_ERROR';
        const estimateUnavailable = estimateStatus === 'UNAVAILABLE'
            || estimateStatus === 'STALE'
            || estimateStatus === 'NOT_ATTEMPTED';
        return {
            key: fund.id,
            id: fund.id,
            fundCode: fund.fundCode,
            fundName: fund.fundName,
            fundSubType: fund.fundSubType,
            investmentTarget: fund.investmentTarget,
            changePct: estimateFetchFailed || estimateUnavailable
                ? null
                : (estimate?.estimatedChangePct ?? fund.dailyChangePct ?? null),
            isEstimated: !estimateFetchFailed && !!estimate,
            estimateFetchFailed,
            estimateStatus,
            estimateTime: estimate?.estimateTime,
            valuationSource: fund.valuationSource,
            valuationDate: fund.valuationDate,
            holdingShares: fund.holdingShares,
            holdingAmount: fund.holdingAmount,
            dailyPnl: fund.dailyPnl,
            totalPnl: fund.totalPnl,
            status: fund.status,
            groups: fund.groups || [],
        };
    });
}

export function selectHoldingRows(rows) {
    return (rows || []).filter((row) => row.status === 'HOLDING' && Number(row.holdingAmount) > 0);
}
