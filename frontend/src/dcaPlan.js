const WEEK_NAMES = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];

export function dcaScheduleText(plan) {
    if (plan?.frequency === 'DAILY') return '每个交易日';
    if (plan?.frequency === 'WEEKLY') return WEEK_NAMES[plan.dayOfWeek] || '-';
    if (plan?.frequency === 'MONTHLY') return plan.dayOfMonth ? `每月${plan.dayOfMonth}号` : '-';
    return '-';
}
