const DEFAULT_POSITION_WARNING_RATIO = 0.3;

const toHoldingAmount = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? number : null;
};

const hasConfirmedHolding = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number > 0;
};

const toWarningRatio = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 && number <= 1
        ? number
        : DEFAULT_POSITION_WARNING_RATIO;
};

/** 为基金列表补充只读的当前市值占比和提醒状态。 */
export function buildFundPositionWarnings(funds) {
    const rows = (funds || []).map((fund) => ({
        fund,
        holdingAmount: toHoldingAmount(fund.holdingAmount),
        hasConfirmedHolding: hasConfirmedHolding(fund.holdingShares),
        positionWarningRatio: toWarningRatio(fund.positionWarningRatio),
        positionWarningEnabled: fund.positionWarningEnabled !== false,
    }));
    const totalHoldingAmount = rows.reduce((total, row) => total + (row.holdingAmount ?? 0), 0);
    const hasIncompletePortfolioValue = rows.some((row) => row.hasConfirmedHolding && row.holdingAmount === null);

    return rows.map((row) => {
        const positionRatio = hasIncompletePortfolioValue || row.holdingAmount === null || totalHoldingAmount <= 0
            ? null
            : row.holdingAmount / totalHoldingAmount;
        return {
            ...row.fund,
            positionRatio,
            positionWarningRatio: row.positionWarningRatio,
            positionWarningEnabled: row.positionWarningEnabled,
            positionWarningExceeded: row.positionWarningEnabled
                && positionRatio !== null
                && positionRatio > row.positionWarningRatio,
        };
    });
}
