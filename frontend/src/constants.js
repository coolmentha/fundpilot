// 后端枚举 → 中文 label 映射（前端展示用）。枚举值字符串需与后端 name() 一致。

export const labels = {
    // FundStatus
    PENDING_HOLDING: '未建仓',
    HOLDING: '持仓中',
    CLEARED: '已清仓',
    // FundCategory
    BROAD_BASE: '宽基',
    SECTOR: '行业',
    ACTIVE: '主动',
    MIXED: '混合',
    // FundSubType（无后端 label，前端补）
    ETF: 'ETF',
    INDEX: '指数',
    INDEX_ENHANCED: '指数增强',
    // StrategyParamStatus
    PENDING_CALIBRATION: '待校准',
    CALIBRATED: '已校准',
    EFFECTIVE: '已生效',
    // SignalType
    NONE: '无建议',
    BUILD: '建仓',
    ADD: '加仓',
    SELL: '卖出',
    // FundTransactionStatus
    PENDING: '待确认',
    CONFIRMED: '已确认',
    CANCELLED: '已取消',
    // FundTransactionSource
    INCREASE: '加仓',
    DECREASE: '减仓',
    TRANSFER_IN: '转入',
    TRANSFER_OUT: '转出',
    INVEST: '定投',
    // WeeklyMacdState
    DIVERGENCE_BOTTOM: '底背离',
    GREEN_SHRINKING: '绿柱缩小',
    RED_SHRINKING: '红柱缩小',
    GREEN_EXPANDING: '绿柱扩大',
    // VolumeState
    LOW_STABLE: '地量企稳',
    NORMAL: '正常',
    HIGH_DROP: '放量下跌',
    // MeasureUnit
    AMOUNT: '金额',
    SHARE: '份额',
};

// 标签颜色：成功态绿、进行中态金、终态默认、动作态蓝。
export const tagColor = (value) => {
    const greens = ['HOLDING', 'CONFIRMED', 'CALIBRATED', 'EFFECTIVE', 'PASSED', 'BUILD', 'INCREASE'];
    const golds = ['PENDING_HOLDING', 'PENDING_CALIBRATION', 'PENDING', 'ADD', 'INVEST'];
    const reds = ['CLEARED', 'CANCELLED', 'SELL', 'DECREASE'];
    if (greens.includes(value)) return 'green';
    if (golds.includes(value)) return 'gold';
    if (reds.includes(value)) return 'red';
    return 'blue';
};

export const text = (value) => labels[value] || (value === 0 ? '0' : (value || '-'));
export const money = (value) => Number(value || 0).toLocaleString('zh-CN', {
    style: 'currency', currency: 'CNY', maximumFractionDigits: 2,
});
export const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`;

// 盈亏/涨跌配色(A 股惯例:正=红、负=绿、零/空=灰)。null 视为无数据。
export const pnlColor = (value) => {
    if (value === null || value === undefined) return undefined;
    const n = Number(value);
    if (n > 0) return '#cf1322';   // 涨/盈 红
    if (n < 0) return '#3f8600';   // 跌/亏 绿
    return undefined;              // 0 默认
};

// 带正负号的金额(null → '-',正数带 + 号,负数自带 - 号)。
export const signedMoney = (value) => {
    if (value === null || value === undefined) return '-';
    const n = Number(value);
    const formatted = Math.abs(n).toLocaleString('zh-CN', {
        style: 'currency', currency: 'CNY', maximumFractionDigits: 2,
    });
    return n > 0 ? `+${formatted}` : formatted;
};

// 涨跌幅展示(null → '-',正数带 + 号)。
export const signedPercent = (value) => {
    if (value === null || value === undefined) return '-';
    const pct = (Number(value) * 100).toFixed(2);
    return Number(value) > 0 ? `+${pct}%` : `${pct}%`;
};

// Instant 字段是 ISO-8601 UTC 字符串（如 2026-06-25T08:00:00Z），截取前 19 位作展示。
export const datetime = (value) => {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 19);
};
export const date = (value) => {
    if (!value) return '-';
    return String(value).slice(0, 10);
};

// FundCategory 下拉选项
export const fundCategoryOptions = [
    {value: 'BROAD_BASE', label: '宽基'},
    {value: 'SECTOR', label: '行业'},
    {value: 'ACTIVE', label: '主动'},
    {value: 'MIXED', label: '混合'},
];

// FundTransactionSource 下拉选项(issue #18 手动录入)
export const fundSourceOptions = [
    {value: 'INCREASE', label: '加仓'},
    {value: 'DECREASE', label: '减仓'},
    {value: 'TRANSFER_IN', label: '转入'},
    {value: 'TRANSFER_OUT', label: '转出'},
    {value: 'INVEST', label: '定投'},
];
