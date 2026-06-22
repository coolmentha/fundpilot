import {useEffect, useMemo, useState} from 'react';
import {
    Alert,
    Button,
    Card,
    ConfigProvider,
    Form,
    Input,
    InputNumber,
    Layout,
    Menu,
    message,
    Modal,
    Popconfirm,
    Segmented,
    Skeleton,
    Space,
    Statistic,
    Table,
    Tabs,
    Tag,
    Typography
} from 'antd';
import {
    BarChartOutlined,
    DatabaseOutlined,
    FundOutlined,
    HistoryOutlined,
    LoginOutlined,
    LogoutOutlined,
    PieChartOutlined,
    ReloadOutlined,
    SearchOutlined,
    SettingOutlined,
    SwapOutlined,
    TransactionOutlined
} from '@ant-design/icons';
import {api} from './mockApi.js';

const {Header, Content, Sider} = Layout;
const {Text, Title} = Typography;

const menuItems = [
    {key: 'dashboard', icon: <PieChartOutlined/>, label: '总览'},
    {key: 'signals', icon: <BarChartOutlined/>, label: '交易信号'},
    {key: 'operations', icon: <TransactionOutlined/>, label: '基金交易'},
    {key: 'backtest', icon: <FundOutlined/>, label: '模拟回测'},
    {key: 'history', icon: <HistoryOutlined/>, label: '历史净值'},
    {key: 'settings', icon: <SettingOutlined/>, label: '预算参数'},
];

const labelMap = {
    ACTIVE: '生效中',
    ARCHIVED: '已归档',
    BEFORE_CONFIRMATION: '待确认',
    BUDGET: '预算',
    BUY: '买入',
    CANCELLED: '已取消',
    CLEARED: '已清仓',
    CONFIRMED: '已确认',
    CONVERT_OUT: '转换卖出',
    DEPOSIT: '外部入金',
    DEPOSIT_CONFIRMED: '入金确认',
    fixedInvestmentAmount: '定投金额',
    fixedInvestment: '定投',
    FUND: '基金',
    FUND_ARCHIVED: '基金归档',
    FUND_AUTO_MANAGED: '自动管理基金',
    FUND_CLEARED: '基金清仓',
    HOLDING: '持仓中',
    PARAMETER: '参数',
    PASSED: '通过',
    PENDING_CALIBRATION: '待校准',
    SAMPLE_INSUFFICIENT: '样本不足',
    SEARCH_RESULT: '搜索结果',
    SELL: '卖出',
    TRANSACTION: '交易',
    TRANSACTION_CONFIRMED: '交易确认',
    TRANSACTION_CREATED: '交易创建',
    provider: '手动录入',
    passed: '通过',
    'fixed-investment': '定投',
    'over-tolerance-band': '超容忍带',
    'passed-with-insufficient-sample': '样本不足但准入',
};

const text = (value) => labelMap[value] || value || '-';
const money = (value) => Number(value || 0).toLocaleString('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 2
});
const percent = (value) => `${(Number(value || 0) * 100).toFixed(2)}%`;

function StatusTag({value}) {
    const color = value === 'CONFIRMED' || value === 'PASSED' || value === 'ACTIVE' ? 'green' : value === 'BEFORE_CONFIRMATION' || value === 'PENDING_CALIBRATION' || value === 'SAMPLE_INSUFFICIENT' ? 'gold' : value === 'CANCELLED' || value === 'ARCHIVED' ? 'default' : 'blue';
    return <Tag color={color}>{text(value)}</Tag>;
}

function Login({onLogin}) {
    const [loading, setLoading] = useState(false);
    const submit = async () => {
        setLoading(true);
        try {
            await api('/api/auth/login', {method: 'POST', body: '{}'});
            onLogin();
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-shell">
            <Card className="login-card" title="Fund Pilot">
                <Space direction="vertical" className="full-width" size={16}>
                    <Text type="secondary">个人基金组合驾驶舱演示版</Text>
                    <Form layout="vertical" onFinish={submit} initialValues={{username: 'demo', password: 'demo'}}>
                        <Form.Item label="用户名" name="username"><Input/></Form.Item>
                        <Form.Item label="密码" name="password"><Input.Password/></Form.Item>
                        <Button block type="primary" htmlType="submit" icon={<LoginOutlined/>}
                                loading={loading}>登录演示系统</Button>
                    </Form>
                </Space>
            </Card>
        </div>
    );
}

function Dashboard({dashboard, refresh}) {
    if (!dashboard) return <Skeleton active paragraph={{rows: 8}}/>;
    const columns = [
        {title: '基金代码', dataIndex: 'fundCode', width: 110},
        {title: '基金名称', dataIndex: 'fundName', ellipsis: true},
        {title: '状态', dataIndex: 'status', render: (value) => <StatusTag value={value}/>},
        {title: '份额', dataIndex: 'shares', align: 'right', render: (value) => Number(value).toFixed(3)},
        {title: '市值', dataIndex: 'marketValue', align: 'right', render: money},
        {
            title: '今日盈亏',
            dataIndex: 'todayProfitLoss',
            align: 'right',
            render: (value) => <Text type={value >= 0 ? 'success' : 'danger'}>{money(value)}</Text>
        },
        {title: '仓位', dataIndex: 'positionRatio', align: 'right', render: percent},
        {title: '准入', dataIndex: 'admissionStatus', render: (value) => <StatusTag value={value}/>},
    ];

    return (
        <>
            <div className="dashboard-toolbar">
                <div>
                    <Text className="toolbar-kicker">FUND PILOT</Text>
                    <Title level={3}>个人投资者看板</Title>
                </div>
                <Button type="primary" icon={<ReloadOutlined/>} onClick={refresh}>刷新看板</Button>
            </div>
            <div className="metric-grid">
                <Card className="metric-card metric-primary"><Statistic title="总预算" value={dashboard.totalBudget}
                                                                        precision={2} prefix="¥"/></Card>
                <Card className="metric-card metric-blue"><Statistic title="持仓市值"
                                                                     value={dashboard.holdingMarketValue} precision={2}
                                                                     prefix="¥"/></Card>
                <Card className="metric-card metric-amber"><Statistic title="可用现金" value={dashboard.availableCash}
                                                                      precision={2} prefix="¥"/></Card>
                <Card className="metric-card metric-profit"><Statistic title="今日盈亏"
                                                                       value={dashboard.todayProfitLoss} precision={2}
                                                                       prefix="¥"
                                                                       valueStyle={{color: dashboard.todayProfitLoss >= 0 ? '#16a34a' : '#dc2626'}}/></Card>
                <Card className="metric-card metric-neutral"><Statistic title="上涨 / 下跌"
                                                                        value={`${dashboard.upCount} / ${dashboard.downCount}`}/></Card>
            </div>
            <Alert className="status-alert" type="success" showIcon
                   message={`净值状态：${text(dashboard.navStatus)}。依赖实时数据的动作在数据异常时会阻断。`}/>
            <Card className="data-card" title="持仓明细">
                <Table rowKey="fundCode" size="small" dataSource={dashboard.holdings} columns={columns}
                       pagination={false} scroll={{x: 900}}/>
            </Card>
        </>
    );
}

function Signals() {
    const [signals, setSignals] = useState([]);
    const [loading, setLoading] = useState(false);
    const load = async () => {
        setLoading(true);
        try {
            setSignals(await api('/api/signals/daily', {method: 'POST', body: '{}'}));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card title="每日信号" extra={<Button type="primary" icon={<BarChartOutlined/>} loading={loading}
                                              onClick={load}>生成今日信号</Button>}>
            <Table rowKey={(row) => `${row.type}-${row.fundCode}`} size="small" dataSource={signals} pagination={false}
                   columns={[
                       {title: '类型', dataIndex: 'type', render: (value) => <StatusTag value={value}/>},
                       {title: '基金代码', dataIndex: 'fundCode'},
                       {title: '金额', dataIndex: 'amount', align: 'right', render: money},
                       {title: '份额', dataIndex: 'shares', align: 'right'},
                       {title: '原因', dataIndex: 'reason', render: text},
                   ]}/>
        </Card>
    );
}

function TradeModal({open, draft, onClose, onSaved}) {
    const [form] = Form.useForm();
    const [reverseForm] = Form.useForm();
    const [previewData, setPreviewData] = useState();
    const [confirming, setConfirming] = useState(false);

    useEffect(() => {
        if (open) {
            form.setFieldsValue({type: 'BUY', ...draft});
            setPreviewData(undefined);
        }
    }, [draft, form, open]);

    const createPreview = async (values) => {
        const payload = {...values, amount: Number(values.amount || 0), shares: Number(values.shares || 0)};
        setPreviewData(await api('/api/transactions/preview', {method: 'POST', body: JSON.stringify(payload)}));
    };

    const save = async () => {
        const values = await form.validateFields();
        const payload = {...values, amount: Number(values.amount || 0), shares: Number(values.shares || 0)};
        setConfirming(true);
        try {
            await api('/api/transactions', {method: 'POST', body: JSON.stringify(payload)});
            message.success(payload.type === 'DEPOSIT' ? '外部入金已确认' : '已生成待确认记录');
            onSaved();
            onClose();
        } finally {
            setConfirming(false);
        }
    };

    return (
        <Modal title="录入交易" open={open} onCancel={onClose} footer={null} width={760} destroyOnHidden>
            <Tabs items={[
                {
                    key: 'entry',
                    label: '买入 / 卖出 / 转换 / 入金',
                    children: (
                        <Form form={form} layout="vertical" initialValues={{type: 'BUY'}} onFinish={createPreview}
                              onValuesChange={() => setPreviewData(undefined)}>
                            <Form.Item label="类型" name="type"><Segmented options={[{value: 'BUY', label: '买入'}, {
                                value: 'SELL',
                                label: '卖出'
                            }, {value: 'DEPOSIT', label: '入金'}, {value: 'CONVERT_OUT', label: '转换'}]}/></Form.Item>
                            <Form.Item noStyle shouldUpdate={(prev, current) => prev.type !== current.type}>
                                {({getFieldValue}) => getFieldValue('type') === 'DEPOSIT' ? null : (
                                    <>
                                        <Form.Item label="基金代码" name="fundCode"
                                                   rules={[{required: true, message: '请输入基金代码'}]}><Input
                                            placeholder="例如 110022"/></Form.Item>
                                        {getFieldValue('type') === 'CONVERT_OUT' &&
                                            <Form.Item label="目标基金代码" name="targetFundCode"
                                                       rules={[{required: true, message: '请输入目标基金代码'}]}><Input
                                                placeholder="例如 000300"/></Form.Item>}
                                    </>
                                )}
                            </Form.Item>
                            <Form.Item noStyle shouldUpdate={(prev, current) => prev.type !== current.type}>
                                {({getFieldValue}) => getFieldValue('type') === 'SELL' || getFieldValue('type') === 'CONVERT_OUT'
                                    ? <Form.Item label="卖出/转换份额" name="shares"
                                                 rules={[{required: true, message: '请输入份额'}]}><InputNumber
                                        min={0.000001} precision={6} className="full-width"/></Form.Item>
                                    : <Form.Item label="买入/入金金额" name="amount"
                                                 rules={[{required: true, message: '请输入金额'}]}><InputNumber
                                        min={0.01} precision={2} className="full-width"/></Form.Item>}
                            </Form.Item>
                            <Space>
                                <Button htmlType="submit">预览明细</Button>
                                <Button type="primary" disabled={!previewData} loading={confirming}
                                        onClick={save}>确认生成</Button>
                            </Space>
                            {previewData && <TradePreview data={previewData}/>}
                        </Form>
                    ),
                },
                {
                    key: 'reverse',
                    label: '受控冲正',
                    children: (
                        <Form form={reverseForm} layout="vertical" onFinish={async (values) => {
                            await api(`/api/transactions/${values.transactionId}/reverse`, {
                                method: 'POST',
                                body: JSON.stringify({reason: values.reason})
                            });
                            message.success('冲正已生成反向流水');
                            reverseForm.resetFields();
                            onSaved();
                            onClose();
                        }}>
                            <Form.Item label="已确认交易 ID" name="transactionId"
                                       rules={[{required: true}]}><Input/></Form.Item>
                            <Form.Item label="冲正原因" name="reason" rules={[{required: true}]}><Input.TextArea
                                rows={2}/></Form.Item>
                            <Button htmlType="submit">生成冲正</Button>
                        </Form>
                    ),
                },
            ]}/>
        </Modal>
    );
}

function TradePreview({data}) {
    const items = [
        data.fundCode && ['基金', `${data.fundCode} ${data.fundName || ''}`],
        data.targetFundCode && ['目标基金', `${data.targetFundCode} ${data.targetFundName || ''}`],
        data.amount > 0 && ['录入金额', money(data.amount)],
        data.shares > 0 && ['录入份额', Number(data.shares).toFixed(6)],
        data.estimatedNav && ['系统取值净值', Number(data.estimatedNav).toFixed(6)],
        data.estimatedFee > 0 && ['预估手续费', money(data.estimatedFee)],
        data.estimatedShares > 0 && ['预估确认份额', Number(data.estimatedShares).toFixed(6)],
        data.estimatedAmount > 0 && ['预估确认金额', money(data.estimatedAmount)],
    ].filter(Boolean);

    return (
        <div className="trade-preview-panel">
            <div className="trade-preview-header">
                <div><Text className="toolbar-kicker">确认明细</Text><Title level={5}>系统确认前明细</Title></div>
                <Space wrap>
                    {data.fundCode && <Tag
                        color={data.fundAutoManaged ? 'gold' : 'green'}>{data.fundAutoManaged ? '将自动加入基金' : '已管理/无需加入'}</Tag>}
                    {data.estimatedNav && <Tag
                        color={data.estimatedNavReal ? 'green' : 'orange'}>{data.estimatedNavReal ? `系统真实净值 ${text(data.navSource)}` : '系统降级估算'}</Tag>}
                </Space>
            </div>
            <div className="preview-grid">
                {items.map(([label, value]) => <div key={label}><Text
                    type="secondary">{label}</Text><strong>{value}</strong></div>)}
            </div>
            {data.warnings?.length > 0 &&
                <Alert className="status-alert" type="warning" showIcon message={data.warnings.join('；')}/>}
        </div>
    );
}

function Operations({refresh}) {
    const [funds, setFunds] = useState([]);
    const [search, setSearch] = useState('沪深300');
    const [results, setResults] = useState([]);
    const [ledgerMap, setLedgerMap] = useState({});
    const [modalOpen, setModalOpen] = useState(false);
    const [draft, setDraft] = useState({type: 'BUY'});

    const loadFunds = async () => setFunds(await api('/api/funds/managed'));
    useEffect(() => {
        loadFunds();
    }, []);

    const openTrade = (nextDraft) => {
        setDraft({type: nextDraft.type || 'BUY', fundCode: nextDraft.fundCode});
        setModalOpen(true);
    };
    const reload = async () => {
        await loadFunds();
        await refresh();
    };
    const clearLedger = async (code) => {
        setLedgerMap((current) => ({...current, [code]: []}));
    };
    const fetchLedger = async (code) => {
        setLedgerMap((current) => ({...current, [code]: []}));
        const rows = await api(`/api/transactions/fund/${encodeURIComponent(code)}/ledger`);
        setLedgerMap((current) => ({...current, [code]: rows}));
    };

    const managedColumns = [
        {title: '代码', dataIndex: 'code', width: 110},
        {title: '名称', dataIndex: 'name', width: 180, ellipsis: true},
        {title: '状态', dataIndex: 'status', width: 110, render: (value) => <StatusTag value={value}/>},
        {title: '持仓金额', dataIndex: 'holdingMarketValue', width: 130, align: 'right', render: money},
        {
            title: '今日盈亏',
            dataIndex: 'todayProfitLoss',
            width: 130,
            align: 'right',
            render: (value) => <Text type={value >= 0 ? 'success' : 'danger'}>{money(value)}</Text>
        },
        {title: '校准', dataIndex: 'admissionReason', ellipsis: true, render: text},
        {
            title: '交易',
            width: 210,
            render: (_, row) => <Space wrap><Button size="small" type="primary" icon={<TransactionOutlined/>}
                                                    onClick={() => openTrade({
                                                        type: 'BUY',
                                                        fundCode: row.code
                                                    })}>买入</Button><Button size="small" onClick={() => openTrade({
                type: 'SELL',
                fundCode: row.code
            })}>卖出</Button><Button size="small" icon={<SwapOutlined/>}
                                     onClick={() => openTrade({type: 'CONVERT_OUT', fundCode: row.code})}>转换</Button></Space>
        },
        {
            title: '维护', width: 150, render: (_, row) => <Space wrap><Button size="small" onClick={async () => {
                await api(`/api/funds/managed/${row.code}/status`, {
                    method: 'POST',
                    body: JSON.stringify({status: 'CLEARED'})
                });
                await reload();
            }}>清仓</Button><Button size="small" onClick={async () => {
                await api(`/api/funds/managed/${row.code}/status`, {
                    method: 'POST',
                    body: JSON.stringify({status: 'ARCHIVED'})
                });
                await reload();
            }}>归档</Button></Space>
        },
    ];

    return (
        <Space direction="vertical" size={16} className="full-width">
            <Card className="fund-trade-command" title="基金与交易工作台"
                  extra={<Space wrap><Button type="primary" icon={<TransactionOutlined/>}
                                             onClick={() => openTrade({type: 'BUY'})}>录入交易</Button><Button
                      icon={<DatabaseOutlined/>} onClick={() => openTrade({type: 'DEPOSIT'})}>外部入金</Button><Button
                      icon={<ReloadOutlined/>} onClick={async () => {
                      const result = await api('/api/transactions/simulate-next-04-confirmation', {
                          method: 'POST',
                          body: '{}'
                      });
                      message.success(`确认 ${result.confirmedCount} 条`);
                      await reload();
                  }}>模拟次日 04:00 回填确认</Button></Space>}>
                <Text type="secondary">基金维护、交易录入、待确认和已确认流水在同一工作台完成。</Text>
            </Card>
            <Card title="基金搜索">
                <Space.Compact className="full-width"><Input value={search}
                                                             onChange={(event) => setSearch(event.target.value)}/><Button
                    icon={<SearchOutlined/>}
                    onClick={async () => setResults(await api(`/api/funds/search?keyword=${encodeURIComponent(search)}`))}>搜索</Button></Space.Compact>
                <Table className="compact-table" rowKey="code" size="small" dataSource={results} pagination={false}
                       columns={[{title: '代码', dataIndex: 'code'}, {title: '名称', dataIndex: 'name'}, {
                           title: '准入',
                           dataIndex: 'admissionStatus',
                           render: (value) => <StatusTag value={value}/>
                       }, {
                           title: '操作',
                           width: 120,
                           render: (_, row) => <Button size="small" type="link" onClick={() => openTrade({
                               type: 'BUY',
                               fundCode: row.code
                           })}>买入</Button>
                       }]}/>
            </Card>
            <Card title="已管理基金" extra={<Text type="secondary">展开基金查看自己的流水。</Text>}>
                <Table rowKey="code" size="small" dataSource={funds} columns={managedColumns} pagination={false}
                       scroll={{x: 1120}} expandable={{
                    onExpand: (expanded, row) => expanded ? fetchLedger(row.code) : clearLedger(row.code),
                    expandedRowRender: (row) => <LedgerTable code={row.code} rows={ledgerMap[row.code] || []}
                                                             refresh={reload} fetchLedger={fetchLedger}/>
                }}/>
            </Card>
            <TradeModal open={modalOpen} draft={draft} onClose={() => setModalOpen(false)} onSaved={reload}/>
        </Space>
    );
}

function LedgerTable({code, rows, refresh, fetchLedger}) {
    return (
        <Table rowKey="id" size="small" className="ledger-table" dataSource={rows} pagination={false} scroll={{x: 780}}
               columns={[
                   {title: '交易类型', dataIndex: 'type', width: 120, render: (value) => <StatusTag value={value}/>},
                   {title: '状态', dataIndex: 'status', width: 150, render: (value) => <StatusTag value={value}/>},
                   {title: '金额', dataIndex: 'amount', width: 120, align: 'right', render: money},
                   {title: '份额', dataIndex: 'shares', width: 120, align: 'right'},
                   {title: '目标基金', dataIndex: 'targetFundCode', width: 120},
                   {title: '原因', dataIndex: 'reason', render: text},
                   {
                       title: '操作',
                       width: 100,
                       render: (_, row) => <Popconfirm title="取消这条未确认交易？" onConfirm={async () => {
                           await api(`/api/transactions/${row.id}/cancel`, {method: 'POST', body: '{}'});
                           message.success('已取消');
                           await fetchLedger(code);
                           await refresh();
                       }}><Button size="small"
                                  disabled={row.status !== 'BEFORE_CONFIRMATION'}>取消</Button></Popconfirm>
                   },
               ]}/>
    );
}

function Settings() {
    const [budgetData, setBudgetData] = useState();
    const [paramData, setParamData] = useState();
    const load = async () => {
        setBudgetData(await api('/api/budgets/current'));
        setParamData(await api('/api/parameters/current'));
    };
    useEffect(() => {
        load();
    }, []);
    const rows = Object.entries(budgetData?.fundBudgets || {}).map(([fundCode, amount]) => ({fundCode, amount}));
    const allocated = rows.reduce((sum, row) => sum + row.amount, 0);

    return (
        <div className="two-col">
            <Card title="预算分配">
                <Space direction="vertical" className="full-width">
                    <InputNumber addonBefore="总预算" min={0} precision={2} value={budgetData?.totalBudget}
                                 className="full-width" onChange={(value) => setBudgetData((current) => current && {
                        ...current,
                        totalBudget: Number(value || 0)
                    })}/>
                    <Table rowKey="fundCode" size="small" pagination={false} dataSource={rows}
                           columns={[{title: '基金', dataIndex: 'fundCode'}, {
                               title: '预算',
                               dataIndex: 'amount',
                               render: (_, row) => <InputNumber min={0} precision={2} value={row.amount}
                                                                className="full-width"
                                                                onChange={(value) => setBudgetData((current) => current && {
                                                                    ...current,
                                                                    fundBudgets: {
                                                                        ...current.fundBudgets,
                                                                        [row.fundCode]: Number(value || 0)
                                                                    }
                                                                })}/>
                           }]}/>
                    <Alert type={allocated > (budgetData?.totalBudget || 0) ? 'error' : 'success'} showIcon
                           message={`已分配 ${money(allocated)}，未分配 ${money((budgetData?.totalBudget || 0) - allocated)}`}/>
                    <Button onClick={async () => {
                        await api('/api/budgets', {method: 'POST', body: JSON.stringify(budgetData)});
                        message.success('预算校验通过并保存');
                        await load();
                    }}>保存预算</Button>
                </Space>
            </Card>
            <Card title="参数配置">
                <Space direction="vertical" className="full-width">
                    <InputNumber addonBefore="单基金仓位上限" min={0} max={1} step={0.01} precision={4}
                                 value={paramData?.singleFundPositionLimit} className="full-width"
                                 onChange={(value) => setParamData((current) => current && {
                                     ...current,
                                     singleFundPositionLimit: Number(value || 0)
                                 })}/>
                    <InputNumber addonBefore="全局容忍带" min={0} max={1} step={0.01} precision={4}
                                 value={paramData?.globalToleranceBand} className="full-width"
                                 onChange={(value) => setParamData((current) => current && {
                                     ...current,
                                     globalToleranceBand: Number(value || 0)
                                 })}/>
                    <InputNumber addonBefore="定投金额" min={0} precision={2} value={paramData?.fixedInvestmentAmount}
                                 className="full-width" onChange={(value) => setParamData((current) => current && {
                        ...current,
                        fixedInvestmentAmount: Number(value || 0)
                    })}/>
                    <Space><StatusTag value={paramData?.status || 'UNKNOWN'}/><StatusTag
                        value={paramData?.calibrationState || 'UNKNOWN'}/></Space>
                    <Button onClick={async () => {
                        setParamData(await api('/api/parameters', {method: 'POST', body: JSON.stringify(paramData)}));
                        message.success('参数已保存为待校准版本');
                    }}>保存参数并触发校准</Button>
                </Space>
            </Card>
        </div>
    );
}

function Backtest() {
    const [result, setResult] = useState();
    const [loading, setLoading] = useState(false);
    const curves = result ? [result.strategy, ...result.baselines] : [];
    const points = curves.flatMap((curve) => curve.points.map((point) => ({...point, curve: curve.name})));
    return (
        <Card title="模拟回测">
            <Button type="primary" loading={loading} onClick={async () => {
                setLoading(true);
                try {
                    setResult(await api('/api/backtests/simulate', {
                        method: 'POST',
                        body: JSON.stringify({fundCode: '000300'})
                    }));
                } finally {
                    setLoading(false);
                }
            }}>触发模拟回测</Button>
            {result && <Space direction="vertical" className="full-width result-block">
                <Alert type={result.admission.passed ? 'success' : 'warning'} showIcon
                       message={`准入${result.admission.passed ? '通过' : '不通过'}：${text(result.admission.reason)}；样本 ${result.sampleDays} 天`}/>
                <MiniLineChart data={points} yKey="value" seriesKey="curve"/>
                <Table size="small" rowKey="name" pagination={false} dataSource={curves}
                       columns={[{title: '曲线', dataIndex: 'name'}, {
                           title: '最终收益率',
                           dataIndex: 'finalReturn',
                           render: percent
                       }, {title: '最大回撤', dataIndex: 'maxDrawdown', render: percent}]}/>
            </Space>}
        </Card>
    );
}

function History() {
    const [events, setEvents] = useState([]);
    const [navCode, setNavCode] = useState('000300');
    const [navRows, setNavRows] = useState([]);
    const loadEvents = async () => setEvents(await api('/api/history'));
    const loadNav = async () => setNavRows(await api(`/api/nav/${encodeURIComponent(navCode)}`));
    useEffect(() => {
        loadEvents();
        loadNav();
    }, []);
    return (
        <div className="two-col">
            <Card title="历史记录" extra={<Button size="small" onClick={loadEvents}>查询</Button>}>
                <Table size="small" rowKey={(row) => `${row.eventType}-${row.createdAt}`} dataSource={events}
                       pagination={false} scroll={{x: 720}}
                       columns={[{title: '事件', dataIndex: 'eventType', render: text}, {
                           title: '对象',
                           dataIndex: 'entityType',
                           render: text
                       }, {title: '原因', dataIndex: 'reason', render: text}, {
                           title: '时间',
                           dataIndex: 'createdAt'
                       }]}/>
            </Card>
            <Card title="净值曲线"
                  extra={<Space><Input size="small" value={navCode} onChange={(event) => setNavCode(event.target.value)}
                                       className="fund-code-input"/><Button size="small" icon={<ReloadOutlined/>}
                                                                            onClick={async () => {
                                                                                await api(`/api/nav/${encodeURIComponent(navCode)}/intraday/refresh`, {
                                                                                    method: 'POST',
                                                                                    body: '{}'
                                                                                });
                                                                                message.success('盘中估值已刷新');
                                                                            }}>刷新盘中估值</Button><Button size="small"
                                                                                                            icon={
                                                                                                                <ReloadOutlined/>}
                                                                                                            onClick={loadNav}>刷新净值</Button></Space>}>
                <MiniLineChart data={navRows.map((row) => ({...row, fund: navCode}))} yKey="nav" seriesKey="fund"/>
                <Table size="small" rowKey="date" dataSource={navRows} pagination={false}
                       columns={[{title: '日期', dataIndex: 'date'}, {title: '净值', dataIndex: 'nav'}, {
                           title: '来源',
                           dataIndex: 'source',
                           render: text
                       }]}/>
            </Card>
        </div>
    );
}

function MiniLineChart({data, yKey, seriesKey}) {
    const series = [...new Set(data.map((row) => row[seriesKey]))];
    const values = data.map((row) => Number(row[yKey]));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const width = 680;
    const height = 220;
    const pointsFor = (name) => data.filter((row) => row[seriesKey] === name).map((row, index, rows) => {
        const x = 36 + (index / Math.max(rows.length - 1, 1)) * (width - 72);
        const y = height - 28 - ((Number(row[yKey]) - min) / Math.max(max - min, 0.0001)) * (height - 56);
        return `${x},${y}`;
    }).join(' ');
    return (
        <div className="chart-panel">
            <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="趋势曲线">
                <line x1="36" y1={height - 28} x2={width - 20} y2={height - 28} stroke="#dbe4f0"/>
                <line x1="36" y1="20" x2="36" y2={height - 28} stroke="#dbe4f0"/>
                {series.map((name, index) => <polyline key={name} fill="none"
                                                       stroke={index === 0 ? '#1e40af' : '#f59e0b'} strokeWidth="3"
                                                       points={pointsFor(name)}/>)}
            </svg>
            <Space wrap>{series.map((name, index) => <Tag key={name}
                                                          color={index === 0 ? 'blue' : 'gold'}>{name}</Tag>)}</Space>
        </div>
    );
}

function Shell({onLogout}) {
    const [active, setActive] = useState('dashboard');
    const [dashboard, setDashboard] = useState();
    const [error, setError] = useState('');
    const refresh = async () => {
        try {
            setDashboard(await api('/api/dashboard/summary'));
            setError('');
        } catch (currentError) {
            setError(currentError.message);
        }
    };
    useEffect(() => {
        refresh();
    }, []);
    const page = useMemo(() => {
        if (active === 'signals') return <Signals/>;
        if (active === 'operations') return <Operations refresh={refresh}/>;
        if (active === 'backtest') return <Backtest/>;
        if (active === 'history') return <History/>;
        if (active === 'settings') return <Settings/>;
        return <Dashboard dashboard={dashboard} refresh={refresh}/>;
    }, [active, dashboard]);

    return (
        <Layout className="app-shell">
            <Sider width={216} theme="light" className="app-sider">
                <div className="brand">Fund Pilot</div>
                <Menu mode="inline" selectedKeys={[active]} items={menuItems} onClick={({key}) => setActive(key)}/>
            </Sider>
            <Layout>
                <Header className="app-header">
                    <Space direction="vertical" size={0}>
                        <Title level={4}>个人投资者看板</Title>
                        <Text type="secondary">公开数据源优先 · 失败时阻断依赖实时数据的动作</Text>
                    </Space>
                    <Space>
                        <Tabs activeKey={active} onChange={setActive}
                              items={menuItems.map((item) => ({key: item.key, label: item.label}))}
                              className="top-tabs"/>
                        <Button icon={<LogoutOutlined/>} onClick={onLogout}>退出</Button>
                    </Space>
                    <Segmented className="mobile-nav" value={active} onChange={setActive}
                               options={menuItems.map((item) => ({value: item.key, label: item.label}))}/>
                </Header>
                <Content className="app-content">
                    {error && <Alert className="status-alert" type="error" showIcon message={`接口不可用：${error}`}/>}
                    {page}
                </Content>
            </Layout>
        </Layout>
    );
}

export default function App() {
    const [authed, setAuthed] = useState(false);
    const [loading, setLoading] = useState(true);
    useEffect(() => {
        api('/api/auth/me').then(() => setAuthed(true)).catch(() => setAuthed(false)).finally(() => setLoading(false));
    }, []);
    const logout = async () => {
        try {
            await api('/api/auth/logout', {method: 'POST', body: '{}'});
        } finally {
            setAuthed(false);
        }
    };
    return (
        <ConfigProvider theme={{
            token: {
                colorPrimary: '#1E40AF',
                colorWarning: '#F59E0B',
                colorText: '#0F172A',
                colorBgLayout: '#F8FAFC',
                borderRadius: 6,
                fontFamily: "'Fira Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif"
            }
        }}>
            {loading ? <div className="login-shell"><Skeleton active paragraph={{rows: 3}} className="login-card"/>
            </div> : authed ? <Shell onLogout={logout}/> : <Login onLogin={() => setAuthed(true)}/>}
        </ConfigProvider>
    );
}
