const toNonNegativeNumber = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : 0;
};

const toPositiveNumber = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? number : null;
};

/** 将后端金额投影为预算进度条需要的稳定显示数据。 */
export function buildDcaBudgetProgress(summary) {
    const investedAmount = toNonNegativeNumber(summary?.investedAmount);
    const futureAmount = toNonNegativeNumber(summary?.futureAmount);
    const minimumFutureAmount = summary?.minimumFutureAmount == null
        ? null : toNonNegativeNumber(summary.minimumFutureAmount);
    const maximumFutureAmount = summary?.maximumFutureAmount == null
        ? null : toNonNegativeNumber(summary.maximumFutureAmount);
    const projectedAmount = investedAmount + futureAmount;
    const monthlyBudget = toPositiveNumber(summary?.monthlyBudget);

    if (monthlyBudget === null) {
        return {
            hasBudget: false,
            investedAmount,
            futureAmount,
            projectedAmount,
            remainingAmount: null,
            overBudgetAmount: null,
            investedPercent: 0,
            futurePercent: 0,
            budgetPercent: 0,
            scale: 0,
            isOverBudget: false,
            minimumFutureAmount,
            maximumFutureAmount,
        };
    }

    const scale = Math.max(monthlyBudget, projectedAmount, 1);
    const difference = monthlyBudget - projectedAmount;
    return {
        hasBudget: true,
        monthlyBudget,
        investedAmount,
        futureAmount,
        projectedAmount,
        remainingAmount: difference >= 0 ? difference : 0,
        overBudgetAmount: difference < 0 ? Math.abs(difference) : 0,
        investedPercent: (investedAmount / scale) * 100,
        futurePercent: (futureAmount / scale) * 100,
        budgetPercent: (monthlyBudget / scale) * 100,
        scale,
        isOverBudget: difference < 0,
        minimumFutureAmount,
        maximumFutureAmount,
    };
}
