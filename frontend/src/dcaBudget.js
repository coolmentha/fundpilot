const toNonNegativeNumber = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : 0;
};

const toPositiveNumber = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? number : null;
};

const toOptionalNonNegativeNumber = (value) => {
    if (value == null) return null;
    const number = Number(value);
    return Number.isFinite(number) && number >= 0 ? number : null;
};

/** 将后端金额投影为预算进度条需要的稳定显示数据。 */
export function buildDcaBudgetProgress(summary) {
    const confirmedInvestedAmount = toNonNegativeNumber(summary?.confirmedInvestedAmount);
    const pendingInvestedAmount = toNonNegativeNumber(summary?.pendingInvestedAmount);
    const investedAmount = confirmedInvestedAmount + pendingInvestedAmount;
    const futureAmount = toNonNegativeNumber(summary?.futureAmount);
    const minimumFutureAmount = toOptionalNonNegativeNumber(summary?.minimumFutureAmount);
    const maximumFutureAmount = toOptionalNonNegativeNumber(summary?.maximumFutureAmount);
    const futurePlans = (summary?.futurePlans || []).map((plan) => ({
        ...plan,
        amount: toOptionalNonNegativeNumber(plan.amount),
        maximumAmount: toOptionalNonNegativeNumber(plan.maximumAmount),
    }));
    const projectedAmount = investedAmount + futureAmount;
    const monthlyBudget = toPositiveNumber(summary?.monthlyBudget);

    if (monthlyBudget === null) {
        return {
            hasBudget: false,
            confirmedInvestedAmount,
            pendingInvestedAmount,
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
            futurePlans,
        };
    }

    const scale = Math.max(monthlyBudget, projectedAmount, 1);
    const difference = monthlyBudget - projectedAmount;
    return {
        hasBudget: true,
        monthlyBudget,
        confirmedInvestedAmount,
        pendingInvestedAmount,
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
        futurePlans,
    };
}
