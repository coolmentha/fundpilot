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
    PENDING_CALIBRATION: '草稿',
    CALIBRATED: '已通过',
    CALIBRATION_FAILED: '未通过',
    EFFECTIVE: '已生效',
    // TakeProfitPhase
    ACCUMULATING: '积累中',
    ARMED: '已启动',
    TRIGGERED: '待止盈',
    COOLDOWN: '冷静期',
    // SignalType
    NONE: '无建议',
    BUILD: '建仓',
    ADD: '加仓',
    SELL: '卖出',
    // SignalActionStatus
    INFORMATIONAL: '无需操作',
    RESPONDED: '已回应',
    IGNORED: '已忽略',
    EXPIRED: '已过期',
    // FundTransactionStatus
    PENDING: '待确认',
    CONFIRMED: '已确认',
    CANCELLED: '已取消',
    // DcaFrequency
    DAILY: '日定投',
    WEEKLY: '周定投',
    MONTHLY: '月定投',
    // DcaPlanStatus
    DRAFT: '草稿',
    EFFECTIVE: '已生效',
    // Backtest passed(非后端枚举,前端回测结果展示用)
    PASSED: '通过',
    FAILED: '未通过',
    // FundTransactionSource
    INCREASE: '加仓',
    DECREASE: '减仓',
    TRANSFER_IN: '转入',
    TRANSFER_OUT: '转出',
    INVEST: '定投',
    ADJUST_IN: '调增',
    ADJUST_OUT: '调减',
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
    // SignalReason(issue #12)
    BUILD: '建仓',
    ADD: '加仓',
    FUND_CLEARED: '基金已清仓',
    NO_STRATEGY: '无生效策略',
    NO_SELL_TRIGGER: '未触发卖出条件',
    BUILD_CONDITION_NOT_MET: '建仓条件未满足',
    NO_ADD_TIER: '无加仓档位触发',
    LOGIC_BROKEN: '逻辑止损',
    NO_TIER_TO_SELL: '无可卖档位',
    TRAILING_STOP: '移动止盈',
    REBALANCE: '再平衡减仓',
    HARD_CONSTRAINT_BREACH: '硬约束违反',
    MIN_HOLD_DAYS_NOT_MET: '持有期不足',
    INSUFFICIENT_MARKET_DATA: '行情数据不足',
};

// 标签颜色：成功态绿、进行中态金、终态默认、动作态蓝。
export const tagColor = (value) => {
    const greens = ['HOLDING', 'CONFIRMED', 'CALIBRATED', 'EFFECTIVE', 'PASSED', 'BUILD', 'INCREASE'];
    const golds = ['PENDING_HOLDING', 'PENDING_CALIBRATION', 'PENDING', 'ADD', 'INVEST'];
    const reds = ['CLEARED', 'CANCELLED', 'SELL', 'DECREASE', 'FAILED', 'CALIBRATION_FAILED'];
    if (greens.includes(value)) return 'green';
    if (golds.includes(value)) return 'gold';
    if (reds.includes(value)) return 'red';
    return 'blue';
};

export const text = (value) => labels[value] || (value === 0 ? '0' : (value || '-'));
export const money = (value) => Number(value || 0).toLocaleString('zh-CN', {
    style: 'currency', currency: 'CNY', maximumFractionDigits: 2,
});
export const percent = (value) => {
    if (value === null || value === undefined) return '-';
    const n = Number(value);
    return Number.isFinite(n) ? `${(n * 100).toFixed(2)}%` : '-';
};

// 盈亏/涨跌配色(A 股惯例:正=红、负=绿、零/空=灰)。null 视为无数据。
export const pnlColor = (value) => {
    if (value === null || value === undefined) return undefined;
    const n = Number(value);
    if (n > 0) return '#cf1322';   // 涨/盈 红
    if (n < 0) return '#3f8600';   // 跌/亏 绿
    return undefined;              // 0 默认
};

// 带正负号的金额(null → '-',正数带 + 号,负数 toLocaleString 自带 - 号)。
export const signedMoney = (value) => {
    if (value === null || value === undefined) return '-';
    const n = Number(value);
    const formatted = n.toLocaleString('zh-CN', {
        style: 'currency', currency: 'CNY', maximumFractionDigits: 2,
    });
    if (n > 0) return `+${formatted}`;
    return formatted; // 0 或负——toLocaleString 对负数自然输出 -¥xxx
};

// 涨跌幅展示(null → '-',正数带 + 号)。
export const signedPercent = (value) => {
    if (value === null || value === undefined) return '-';
    const pct = (Number(value) * 100).toFixed(2);
    return Number(value) > 0 ? `+${pct}%` : `${pct}%`;
};

const instantParts = (value, options) => {
    if (!value) return null;
    const instant = new Date(value);
    if (Number.isNaN(instant.getTime())) return null;
    return Object.fromEntries(new Intl.DateTimeFormat('zh-CN', {
        timeZone: 'Asia/Shanghai', hourCycle: 'h23', ...options,
    }).formatToParts(instant).map(({type, value: part}) => [type, part]));
};

// 后端 Instant 统一按北京时间展示，避免直接截取 UTC 字符串造成 8 小时时差。
export const datetime = (value) => {
    const parts = instantParts(value, {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
    return parts
        ? `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
        : '-';
};
export const date = (value) => {
    const parts = instantParts(value, {year: 'numeric', month: '2-digit', day: '2-digit'});
    return parts ? `${parts.year}-${parts.month}-${parts.day}` : '-';
};

// FundCategory 下拉选项
export const fundCategoryOptions = [
    {value: 'BROAD_BASE', label: '宽基'},
    {value: 'SECTOR', label: '行业'},
    {value: 'ACTIVE', label: '主动'},
    {value: 'MIXED', label: '混合'},
];

// 大数缩写(成交额/资金流向用):亿/万。null/0/负 → 原样返回。
export const compactMoney = (value) => {
    if (value === null || value === undefined) return '-';
    const n = Number(value);
    if (!isFinite(n)) return '-';
    const abs = Math.abs(n);
    if (abs >= 1e8) return `${(n / 1e8).toFixed(2)}亿`;
    if (abs >= 1e4) return `${(n / 1e4).toFixed(2)}万`;
    return n.toFixed(2);
};

// 带正负号的大数缩写(资金流向净额用)。
export const signedCompactMoney = (value) => {
    if (value === null || value === undefined) return '-';
    const n = Number(value);
    const formatted = compactMoney(n);
    if (formatted === '-') return '-';
    return n > 0 ? `+${formatted}` : formatted;
};

// FundTransactionSource 下拉选项(issue #18 手动录入)
export const fundSourceOptions = [
    {value: 'INCREASE', label: '加仓'},
    {value: 'DECREASE', label: '减仓'},
    {value: 'TRANSFER_IN', label: '转入'},
    {value: 'TRANSFER_OUT', label: '转出'},
    {value: 'INVEST', label: '定投'},
    {value: 'ADJUST_IN', label: '调增'},
    {value: 'ADJUST_OUT', label: '调减'},
];

// 后端 ErrorCode → 友好标题映射(报错弹窗用)。枚举值需与后端 ErrorCode.name() 一致。
// 设计原则(ui-ux-pro-max):错误需可被读屏 announced、提供 recovery 线索、信息清晰可看清。
export const errorTitles = {
    // 资源未找到
    FUND_NOT_FOUND: '基金不存在',
    STRATEGY_NOT_FOUND: '策略不存在',
    TRANSACTION_NOT_FOUND: '交易不存在',
    SIGNAL_LOG_NOT_FOUND: '信号不存在',
    DCA_PLAN_NOT_FOUND: '定投计划不存在',
    ENTITY_NOT_FOUND: '记录不存在',
    MISSING_FUND_IDENTITY: '缺少基金身份信息',
    // 输入校验
    FUND_CATEGORY_REQUIRED: '缺少基金类型',
    MANUAL_TRANSACTION_FIELD_REQUIRED: '手动交易字段缺失',
    COST_PER_SHARE_INVALID: '成本单价不合法',
    INITIAL_MARKET_VALUE_INVALID: '初始持仓市值不合法',
    DEPOSIT_AMOUNT_INVALID: '入金金额不合法',
    POSITION_LIMIT_INVALID: '仓位上限不合法',
    CAPITAL_POOL_NOT_CONFIGURED: '总资金池尚未配置',
    POSITION_LIMIT_EXCEEDED: '超过单基金仓位上限',
    STRATEGY_PARAM_INVALID: '策略参数不合法',
    DCA_PLAN_INVALID: '定投计划参数不合法',
    SIGNAL_OPERATION_VALUE_INVALID: '实际操作数值不合法',
    OPENED_AT_IN_FUTURE: '建仓时间晚于当前时间',
    // 交易/信号状态非法
    TRANSACTION_ALREADY_CONFIRMED: '交易已确认',
    TRANSACTION_ALREADY_CANCELLED: '交易已撤销',
    INVALID_SIGNAL_TYPE: '信号类型非法',
    MISSING_TRIGGER_TIER: '缺少触发档位',
    INVALID_TRIGGER_TIER: '触发档位非法',
    MISSING_ACTUAL_AMOUNT: '缺少实际金额',
    MISSING_ACTUAL_SHARES: '缺少实际份额',
    UNSUPPORTED_SELL_REASON: '卖出原因不支持',
    SIGNAL_ALREADY_RESPONDED: '信号已回应',
    SIGNAL_ALREADY_IGNORED: '信号已忽略',
    SIGNAL_EXPIRED: '信号已过期',
    SIGNAL_FUND_MISMATCH: '信号与基金不匹配',
    NO_VALID_BACKTEST: '无有效回测',
    ILLEGAL_STATE_TRANSITION: '状态切换非法',
    INSUFFICIENT_HOLDING_SHARES: '持仓份额不足',
    INSUFFICIENT_LOTS: '可用持仓批次不足',
    FUND_HAS_PENDING_TRANSACTIONS: '基金存在待确认交易',
    // 寻优
    OPTIMIZATION_NO_VALID_PARAMS: '寻优未达标',
    // 数据源
    NAV_HISTORY_EMPTY: '净值历史为空',
    MARKET_DATA_ALL_SOURCES_FAILED: '行情数据源全部失败',
    // 全站 API 鉴权
    ADMIN_UNAUTHORIZED: '访问凭据无效',
    ADMIN_AUTH_NOT_CONFIGURED: '访问鉴权未配置',
    // 兜底
    INTERNAL_ERROR: '服务异常',
    NETWORK_ERROR: '网络异常',
    BAD_RESPONSE: '响应异常',
};

// 报错标题:未命中映射时回退到 code 本身或通用文案。
export const errorTitle = (code) => errorTitles[code] || (code ? code : '操作失败');
