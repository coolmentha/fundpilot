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
            valuationFirstSeenAt: fund.valuationFirstSeenAt,
            valuationNav: fund.valuationNav,
            holdingShares: fund.holdingShares,
            holdingAmount: fund.holdingAmount,
            dailyPnl: fund.dailyPnl,
            totalPnl: fund.totalPnl,
            returnRate: fund.returnRate,
            status: fund.status,
            groups: fund.groups || [],
        };
    });
}

export function selectHoldingRows(rows) {
    return (rows || []).filter((row) => row.status === 'HOLDING' && Number(row.holdingAmount) > 0);
}

export function selectContributors(funds) {
    const rows = selectHoldingRows(funds)
        .filter((fund) => Number.isFinite(Number(fund.dailyPnl)));
    return {
        contributor: rows.filter((fund) => Number(fund.dailyPnl) > 0)
            .sort((a, b) => Number(b.dailyPnl) - Number(a.dailyPnl))[0] || null,
        detractor: rows.filter((fund) => Number(fund.dailyPnl) < 0)
            .sort((a, b) => Number(a.dailyPnl) - Number(b.dailyPnl))[0] || null,
    };
}

export function mainforceRatio(sector) {
    if (sector?.mainforceNet == null || sector?.turnover == null) return null;
    const net = Number(sector.mainforceNet);
    const turnover = Number(sector.turnover);
    return Number.isFinite(net) && Number.isFinite(turnover) && turnover > 0 ? net / turnover : null;
}

export function sortSectors(sectors, sortBy) {
    return [...(sectors || [])].sort((left, right) => {
        const a = sectorSortValue(left, sortBy);
        const b = sectorSortValue(right, sortBy);
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return b - a;
    });
}

function sectorSortValue(sector, sortBy) {
    const raw = sector?.[sortBy];
    if (sortBy !== 'mainforceRatio' && raw == null) return null;
    const value = sortBy === 'mainforceRatio' ? mainforceRatio(sector) : Number(raw);
    return Number.isFinite(value) ? value : null;
}
