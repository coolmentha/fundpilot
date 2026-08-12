const WEEK_NAMES = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const STRATEGY_NAMES = {
    FIXED: '固定金额',
    LOW_VALUATION: '低估加投',
    MOVING_AVERAGE: '均线策略',
    CHANGE_RATE: '涨跌幅策略',
};
const DECISION_REASONS = {
    LOW_VALUATION: '估值处于低估区',
    MOVING_AVERAGE: '按均线策略执行',
    CHANGE_RATE: '按持仓涨跌幅执行',
    VALUATION_NOT_LOW: '指数估值未处于低估区',
    VALUATION_UNAVAILABLE: '指数估值数据不可用',
    INDEX_KLINE_UNAVAILABLE: '指数均线数据不可用',
    NAV_UNAVAILABLE: '基金最新净值不可用',
    COST_UNAVAILABLE: '平均持仓成本不可用',
};

export function dcaScheduleText(plan) {
    if (plan?.frequency === 'DAILY') return '每个交易日';
    if (plan?.frequency === 'WEEKLY') return WEEK_NAMES[plan.dayOfWeek] || '-';
    if (plan?.frequency === 'MONTHLY') return plan.dayOfMonth ? `每月${plan.dayOfMonth}号` : '-';
    return '-';
}

export function dcaPlanState(plan) {
    if (plan?.status !== 'EFFECTIVE') return {label: '已停用'};
    if (!plan.enabled) return {label: '已暂停'};
    return {label: '运行中', color: 'green'};
}

export function canDeleteDcaPlan(plan) {
    return plan?.status === 'DRAFT';
}

export function dcaStrategyText(strategy) {
    return STRATEGY_NAMES[strategy] || STRATEGY_NAMES.FIXED;
}

export function dcaDecisionReason(decision) {
    if (!decision) return '-';
    return DECISION_REASONS[decision.reasonCode] || decision.reason || '-';
}
