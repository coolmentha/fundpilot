export function isQueryDataReady({data, isLoading, isError}) {
    return data !== undefined && data !== null && !isLoading && !isError;
}

export function estimateStatusText(status) {
    if (status === 'TIMEOUT' || status === 'PARSE_ERROR') return '估值拉取失败';
    if (status === 'UNAVAILABLE' || status === 'STALE' || status === 'NOT_ATTEMPTED') return '暂无估值';
    return null;
}

/**
 * 持仓收益率：总盈亏 / 持仓成本（市值 - 总盈亏）。基金列表、行情工作台和基金详情共用同一口径，
 * 数据缺失或成本非正时返回 null。
 */
export function holdingReturnRate(fund) {
    if (fund?.holdingAmount == null || fund?.totalPnl == null) return null;
    const holdingAmount = Number(fund.holdingAmount);
    const totalPnl = Number(fund.totalPnl);
    const holdingCost = holdingAmount - totalPnl;
    return Number.isFinite(holdingAmount) && Number.isFinite(totalPnl) && holdingCost > 0
        ? totalPnl / holdingCost
        : null;
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
            key: fund.portfolioFundId,
            id: fund.portfolioFundId,
            portfolioFundId: fund.portfolioFundId,
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
            holdingReturnRate: holdingReturnRate(fund),
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

/** 行业主力资金方向:'inflow'(净流入) / 'outflow'(净流出);无资金数据或恰好为 0 时返回 null。 */
export function sectorFlowDirection(sector) {
    const raw = sector?.mainforceNet;
    if (raw == null) return null;
    const net = Number(raw);
    if (!Number.isFinite(net) || net === 0) return null;
    return net > 0 ? 'inflow' : 'outflow';
}

/** 资金方向筛选;direction 非 'inflow'/'outflow' 时返回全量副本。 */
export function filterSectors(sectors, direction) {
    if (direction !== 'inflow' && direction !== 'outflow') return [...(sectors || [])];
    return (sectors || []).filter((sector) => sectorFlowDirection(sector) === direction);
}

/** 行业排序;默认降序,缺失值恒排末尾(升序时也不提前)。 */
export function sortSectors(sectors, sortBy, ascending = false) {
    return [...(sectors || [])].sort((left, right) => {
        const a = sectorSortValue(left, sortBy);
        const b = sectorSortValue(right, sortBy);
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return ascending ? a - b : b - a;
    });
}

function sectorSortValue(sector, sortBy) {
    const raw = sector?.[sortBy];
    if (sortBy !== 'mainforceRatio' && raw == null) return null;
    const value = sortBy === 'mainforceRatio' ? mainforceRatio(sector) : Number(raw);
    return Number.isFinite(value) ? value : null;
}
