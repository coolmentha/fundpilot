const funds = [
    {
        code: '000300',
        name: '沪深300ETF',
        source: 'mock',
        status: 'HOLDING',
        admissionStatus: 'PASSED',
        admissionReason: 'passed',
        holdingMarketValue: 32000,
        todayProfitLoss: 186.25
    },
    {
        code: '110022',
        name: '易方达消费行业',
        source: 'mock',
        status: 'HOLDING',
        admissionStatus: 'PASSED',
        admissionReason: 'passed',
        holdingMarketValue: 23800,
        todayProfitLoss: -72.4
    },
    {
        code: '161725',
        name: '白酒指数',
        source: 'mock',
        status: 'PENDING_CALIBRATION',
        admissionStatus: 'SAMPLE_INSUFFICIENT',
        admissionReason: 'passed-with-insufficient-sample',
        holdingMarketValue: 9600,
        todayProfitLoss: 41.6
    },
];

let loggedIn = false;
let budget = {totalBudget: 120000, fundBudgets: {'000300': 36000, '110022': 28000, '161725': 12000}};
let params = {
    singleFundPositionLimit: 0.3,
    globalToleranceBand: 0.05,
    fixedInvestmentAmount: 1000,
    status: 'ACTIVE',
    calibrationState: 'PASSED'
};
let transactionSeq = 4;

const ledger = [
    {
        id: 'tx-1',
        type: 'BUY',
        status: 'CONFIRMED',
        amount: 12000,
        shares: 3920.225,
        fundCode: '000300',
        reason: 'fixed-investment'
    },
    {
        id: 'tx-2',
        type: 'BUY',
        status: 'CONFIRMED',
        amount: 8000,
        shares: 1865.337,
        fundCode: '110022',
        reason: 'fixed-investment'
    },
    {
        id: 'tx-3',
        type: 'SELL',
        status: 'BEFORE_CONFIRMATION',
        amount: 0,
        shares: 200,
        fundCode: '161725',
        reason: 'over-tolerance-band'
    },
];

const history = [
    {
        eventType: 'TRANSACTION_CONFIRMED',
        entityType: 'TRANSACTION',
        entityId: 'tx-1',
        reason: 'fixed-investment',
        createdAt: '2026-06-20T04:00:00'
    },
    {
        eventType: 'FUND_AUTO_MANAGED',
        entityType: 'FUND',
        entityId: '000300',
        reason: 'passed',
        createdAt: '2026-06-19T21:15:00'
    },
    {
        eventType: 'DEPOSIT_CONFIRMED',
        entityType: 'BUDGET',
        entityId: 'budget-1',
        reason: 'provider',
        createdAt: '2026-06-18T09:30:00'
    },
];

const navSeries = {
    '000300': [
        {date: '2026-06-17', nav: 1.018, source: 'eastmoney'},
        {date: '2026-06-18', nav: 1.024, source: 'eastmoney'},
        {date: '2026-06-19', nav: 1.019, source: 'eastmoney'},
        {date: '2026-06-20', nav: 1.031, source: 'eastmoney-intraday'},
    ],
    '110022': [
        {date: '2026-06-17', nav: 4.255, source: 'eastmoney'},
        {date: '2026-06-18', nav: 4.286, source: 'eastmoney'},
        {date: '2026-06-19', nav: 4.271, source: 'eastmoney'},
        {date: '2026-06-20', nav: 4.293, source: 'eastmoney-intraday'},
    ],
    '161725': [
        {date: '2026-06-17', nav: 1.082, source: 'eastmoney'},
        {date: '2026-06-18', nav: 1.094, source: 'eastmoney'},
        {date: '2026-06-19', nav: 1.088, source: 'eastmoney'},
        {date: '2026-06-20', nav: 1.097, source: 'eastmoney-intraday'},
    ],
};

const wait = (value) => new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), 120));
const fundName = (code) => funds.find((fund) => fund.code === code)?.name || `基金 ${code || ''}`;

function dashboard() {
    const holdings = funds.filter((fund) => fund.status !== 'ARCHIVED').map((fund) => ({
        fundCode: fund.code,
        fundName: fund.name,
        status: fund.status,
        shares: fund.code === '000300' ? 3920.225 : fund.code === '110022' ? 1865.337 : 8750.12,
        marketValue: fund.holdingMarketValue,
        todayProfitLoss: fund.todayProfitLoss,
        positionRatio: fund.holdingMarketValue / budget.totalBudget,
        navSource: '盘中估值',
        admissionStatus: fund.admissionStatus,
    }));
    const holdingMarketValue = holdings.reduce((sum, row) => sum + row.marketValue, 0);
    const todayProfitLoss = holdings.reduce((sum, row) => sum + row.todayProfitLoss, 0);
    return {
        totalBudget: budget.totalBudget,
        holdingMarketValue,
        availableCash: budget.totalBudget - holdingMarketValue,
        todayProfitLoss,
        upCount: holdings.filter((row) => row.todayProfitLoss >= 0).length,
        downCount: holdings.filter((row) => row.todayProfitLoss < 0).length,
        navStatus: 'FRESH',
        holdings,
    };
}

function preview(payload) {
    const nav = payload.fundCode === '110022' ? 4.293 : payload.fundCode === '161725' ? 1.097 : 1.031;
    const amount = Number(payload.amount || 0);
    const shares = Number(payload.shares || 0);
    return {
        type: payload.type,
        fundCode: payload.fundCode,
        fundName: fundName(payload.fundCode),
        targetFundCode: payload.targetFundCode,
        targetFundName: fundName(payload.targetFundCode),
        fundAutoManaged: Boolean(payload.fundCode && !funds.some((fund) => fund.code === payload.fundCode)),
        amount,
        shares,
        estimatedNav: payload.type === 'DEPOSIT' ? undefined : nav,
        navSource: 'eastmoney-intraday',
        estimatedNavReal: payload.type !== 'DEPOSIT',
        estimatedFee: payload.type === 'BUY' ? Math.max(amount * 0.001, 0) : 0,
        estimatedShares: payload.type === 'BUY' ? Number((amount / nav).toFixed(6)) : 0,
        estimatedAmount: payload.type === 'SELL' || payload.type === 'CONVERT_OUT' ? Number((shares * nav).toFixed(2)) : amount,
        warnings: payload.type === 'BUY' && amount > budget.totalBudget * 0.3 ? ['单只满仓30%的规则不能被破坏，当前为演示预警。'] : [],
    };
}

function backtest() {
    const strategy = {
        name: '策略',
        finalReturn: 0.126,
        maxDrawdown: 0.047,
        points: [{date: '2026-03-01', value: 1}, {date: '2026-04-01', value: 1.036}, {
            date: '2026-05-01',
            value: 1.071
        }, {date: '2026-06-01', value: 1.126}]
    };
    const baseline = {
        name: '沪深300',
        finalReturn: 0.082,
        maxDrawdown: 0.063,
        points: [{date: '2026-03-01', value: 1}, {date: '2026-04-01', value: 1.018}, {
            date: '2026-05-01',
            value: 1.041
        }, {date: '2026-06-01', value: 1.082}]
    };
    return {
        fundCode: '000300',
        sampleDays: 260,
        admission: {passed: true, sampleInsufficient: false, reason: 'passed'},
        strategy,
        baselines: [baseline]
    };
}

export async function api(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const payload = options.body ? JSON.parse(options.body) : options;

    if (path === '/api/auth/me') {
        if (!loggedIn) throw new Error('standalone login required');
        return wait({username: 'demo'});
    }
    if (path === '/api/auth/login' && method === 'POST') {
        loggedIn = true;
        return wait({username: 'demo'});
    }
    if (path === '/api/auth/logout' && method === 'POST') {
        loggedIn = false;
        return wait({ok: true});
    }
    if (path === '/api/dashboard/summary') return wait(dashboard());
    if (path === '/api/signals/daily' && method === 'POST') return wait([{
        type: 'BUY',
        fundCode: '000300',
        amount: params.fixedInvestmentAmount,
        shares: 970.873,
        reason: 'fixed-investment'
    }, {type: 'SELL', fundCode: '161725', amount: 0, shares: 200, reason: 'over-tolerance-band'}]);
    if (path === '/api/funds/managed') return wait(funds);
    if (path.startsWith('/api/funds/search')) return wait(funds.map((fund) => ({...fund, status: 'SEARCH_RESULT'})));
    if (path.startsWith('/api/funds/managed/') && path.endsWith('/status') && method === 'POST') {
        const code = decodeURIComponent(path.split('/')[4]);
        const fund = funds.find((item) => item.code === code);
        if (fund) fund.status = payload.status || fund.status;
        history.unshift({
            eventType: fund?.status === 'ARCHIVED' ? 'FUND_ARCHIVED' : 'FUND_CLEARED',
            entityType: 'FUND',
            entityId: code,
            reason: 'provider',
            createdAt: new Date().toISOString()
        });
        return wait({ok: true});
    }
    if (path.startsWith('/api/transactions/fund/') && path.endsWith('/ledger')) {
        const code = decodeURIComponent(path.split('/')[4]);
        return wait(ledger.filter((transaction) => transaction.fundCode === code || transaction.targetFundCode === code));
    }
    if (path === '/api/transactions/preview' && method === 'POST') return wait(preview(payload));
    if (path === '/api/transactions' && method === 'POST') {
        const transaction = {
            id: `tx-${transactionSeq++}`,
            status: payload.type === 'DEPOSIT' ? 'CONFIRMED' : 'BEFORE_CONFIRMATION',
            amount: Number(payload.amount || 0),
            shares: Number(payload.shares || 0),
            reason: payload.type === 'DEPOSIT' ? 'provider' : 'fixed-investment', ...payload
        };
        ledger.unshift(transaction);
        if (payload.type === 'DEPOSIT') budget = {
            ...budget,
            totalBudget: budget.totalBudget + Number(payload.amount || 0)
        };
        if (payload.fundCode && !funds.some((fund) => fund.code === payload.fundCode)) {
            funds.push({
                code: payload.fundCode,
                name: fundName(payload.fundCode),
                source: 'mock',
                status: 'PENDING_CALIBRATION',
                admissionStatus: 'SAMPLE_INSUFFICIENT',
                admissionReason: 'passed-with-insufficient-sample',
                holdingMarketValue: 0,
                todayProfitLoss: 0
            });
        }
        history.unshift({
            eventType: payload.type === 'DEPOSIT' ? 'DEPOSIT_CONFIRMED' : 'TRANSACTION_CREATED',
            entityType: payload.type === 'DEPOSIT' ? 'BUDGET' : 'TRANSACTION',
            entityId: transaction.id,
            reason: transaction.reason,
            createdAt: new Date().toISOString()
        });
        return wait(transaction);
    }
    if (path.endsWith('/cancel') && method === 'POST') {
        const transaction = ledger.find((item) => item.id === path.split('/')[3]);
        if (transaction) transaction.status = 'CANCELLED';
        return wait({ok: true});
    }
    if (path.endsWith('/reverse') && method === 'POST') {
        history.unshift({
            eventType: 'REVERSAL_CREATED',
            entityType: 'TRANSACTION',
            entityId: path.split('/')[3],
            reason: payload.reason || 'provider',
            createdAt: new Date().toISOString()
        });
        return wait({ok: true});
    }
    if (path === '/api/transactions/simulate-next-04-confirmation' && method === 'POST') {
        let confirmedCount = 0;
        ledger.forEach((transaction) => {
            if (transaction.status === 'BEFORE_CONFIRMATION') {
                transaction.status = 'CONFIRMED';
                confirmedCount += 1;
            }
        });
        history.unshift({
            eventType: 'TRANSACTION_CONFIRMED',
            entityType: 'TRANSACTION',
            entityId: 'batch',
            reason: 'provider',
            createdAt: new Date().toISOString()
        });
        return wait({confirmedCount, blocked: false, reason: 'passed'});
    }
    if (path === '/api/budgets/current') return wait(budget);
    if (path === '/api/budgets' && method === 'POST') {
        budget = payload;
        history.unshift({
            eventType: 'BUDGET',
            entityType: 'BUDGET',
            entityId: 'current',
            reason: 'provider',
            createdAt: new Date().toISOString()
        });
        return wait(budget);
    }
    if (path === '/api/parameters/current') return wait(params);
    if (path === '/api/parameters' && method === 'POST') {
        params = {...payload, status: 'PENDING_CALIBRATION'};
        history.unshift({
            eventType: 'PARAMETER',
            entityType: 'PARAMETER',
            entityId: 'current',
            reason: 'provider',
            createdAt: new Date().toISOString()
        });
        return wait(params);
    }
    if (path === '/api/backtests/simulate' && method === 'POST') return wait(backtest());
    if (path.startsWith('/api/history')) return wait(history);
    if (path.includes('/intraday/refresh') || path.endsWith('/refresh')) return wait({ok: true});
    if (path.startsWith('/api/nav/')) return wait(navSeries[decodeURIComponent(path.split('/')[3] || '000300')] || navSeries['000300']);

    return wait({ok: true});
}
