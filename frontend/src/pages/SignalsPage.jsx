import {useState} from 'react';
import {
    App,
    Button,
    Card,
    DatePicker,
    Form,
    InputNumber,
    Modal,
    Popconfirm,
    Select,
    Space,
    Table,
    Typography
} from 'antd';
import {Link, useNavigate, useSearchParams} from 'react-router-dom';
import {ReloadOutlined} from '@ant-design/icons';
import {
    useConfirmOperation,
    useFunds,
    useIgnoreSignal,
    usePendingSignals,
    useSignalsRange,
    useSignalsToday,
} from '../api/hooks.js';
import {datetime, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';
import QueryErrorState from '../components/QueryErrorState.jsx';
import {isQueryDataReady} from '../querySafety.js';

const {Title, Text} = Typography;
const {RangePicker} = DatePicker;

const signalColumns = (extraCol) => [
    {title: '状态', dataIndex: 'responseStatus', width: 100, render: (v) => <StatusTag value={v}/>},
    {title: '类型', dataIndex: 'action', width: 90, render: (v) => <StatusTag value={v}/>},
    {title: '档位', dataIndex: 'triggerTier', width: 70, render: (v) => v ?? '-'},
    {title: '系数', dataIndex: 'coefficient', width: 80, render: (v) => v == null ? '-' : <span className="num-cell">{Number(v).toFixed(4)}</span>},
    {title: '原因', dataIndex: 'reason', render: text},
    {title: '建议量', width: 120, render: (_, r) => {
        const m = r.suggestedMeasure;
        return m ? <span className="num-cell">{Number(m.value).toFixed(2)} ({text(m.measureUnit)})</span> : '-';
    }},
    {title: '警告', dataIndex: 'warnings', render: (v) => v ? <Text type="warning">{v}</Text> : '-'},
    {title: '建议时间', dataIndex: 'signalDate', width: 170, render: datetime},
    ...(extraCol ? [extraCol] : []),
];

export default function SignalsPage() {
    const {message} = App.useApp();
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const fundIdParam = params.get('fundId');
    const fundId = fundIdParam ? Number(fundIdParam) : null;
    const {data: funds, isLoading: fundsLoading, isError: fundsError} = useFunds();
    const {data: pendingSignals, isLoading: pendingLoading, isError: pendingError,
        refetch: refetchPending} = usePendingSignals();
    const [modal, setModal] = useState({open: false, signal: null});
    const [form] = Form.useForm();
    const confirmOp = useConfirmOperation(modal.signal?.fundId);
    const ignoreSignal = useIgnoreSignal();
    const fundsReady = isQueryDataReady({data: funds, isLoading: fundsLoading, isError: fundsError});
    const {
        data: todaySignal,
        isLoading: todayLoading,
        isError: todayError,
        refetch: refetchToday,
    } = useSignalsToday(fundId);
    const [range, setRange] = useState(null);
    const from = range?.[0]?.format('YYYY-MM-DD');
    const to = range?.[1]?.format('YYYY-MM-DD');
    const {
        data: rangeSignals,
        isLoading: rangeLoading,
        isError: rangeError,
        refetch: refetchRange,
    } = useSignalsRange(fundId, from, to);

    const fundOptions = (funds || []).map((f) => ({
        value: String(f.id),
        label: `${f.fundCode} · ${f.fundName}`,
    }));
    const fundName = (id) => funds?.find((fund) => fund.id === id)?.fundName || `基金 #${id}`;
    const holdingShares = (id) => Number(funds?.find((fund) => fund.id === id)?.holdingShares || 0);
    const openConfirm = (signal) => {
        const isSell = signal.action === 'SELL';
        const isLogicBroken = isSell && signal.reason === 'LOGIC_BROKEN';
        const maxShares = holdingShares(signal.fundId);
        setModal({open: true, signal});
        form.setFieldsValue({
            actualAmount: isSell ? undefined : signal.suggestedMeasure?.value,
            actualShares: isLogicBroken ? maxShares : isSell && maxShares > 0
                ? Math.min(Number(signal.suggestedMeasure?.value || 0), maxShares) : undefined,
        });
    };
    const submit = async () => {
        if (modal.signal?.action === 'SELL' && !fundsReady) return;
        const values = await form.validateFields();
        const transaction = await confirmOp.mutateAsync({
            adviceId: modal.signal.id,
            actualAmount: values.actualAmount ?? null,
            actualShares: values.actualShares ?? null,
        });
        message.success('建议已采纳，待确认交易已生成');
        setModal({open: false, signal: null});
        navigate(`/confirm?transactionId=${transaction.transactionId}`);
    };
    const pendingActionColumn = {
        title: '操作', width: 150, render: (_, signal) => (
            <Space size={0}>
                <Button type="link" size="small"
                        disabled={signal.action === 'SELL' && !fundsReady}
                        onClick={() => openConfirm(signal)}>采纳</Button>
                <Popconfirm title="忽略本次建议？" onConfirm={async () => {
                    await ignoreSignal.mutateAsync({signalId: signal.id});
                    message.success('建议已忽略');
                }}>
                    <Button type="link" size="small">忽略</Button>
                </Popconfirm>
            </Space>
        ),
    };
    const isSell = modal.signal?.action === 'SELL';
    const isLogicBroken = isSell && modal.signal?.reason === 'LOGIC_BROKEN';
    const currentHoldingShares = holdingShares(modal.signal?.fundId);
    const transactionColumn = {
        title: '交易', width: 120, render: (_, signal) => signal.relatedTransactionId ? (
            <Space direction="vertical" size={0}>
                <Link to={`/funds/${signal.fundId}?transactionId=${signal.relatedTransactionId}`}>查看交易</Link>
                <Text type="secondary">{text(signal.relatedTransactionStatus)}</Text>
            </Space>
        ) : '-',
    };

    return (
        <Space direction="vertical" size={16} className="full-width">
            <Card title={<Title level={4}>待回应建议</Title>}
                  extra={<Button icon={<ReloadOutlined/>} onClick={() => refetchPending()}>刷新</Button>}>
                {pendingError ? <QueryErrorState onRetry={refetchPending} description="待回应建议加载失败"/> : (
                    <Table rowKey="id" size="small" loading={pendingLoading} dataSource={pendingSignals || []}
                           columns={[
                               {title: '基金', width: 180, render: (_, row) => fundName(row.fundId)},
                               ...signalColumns(pendingActionColumn),
                           ]}
                           pagination={false} scroll={{x: 1050}}
                           locale={{emptyText: <EmptyState description="暂无待回应建议"/>}}/>
                )}
            </Card>
            <Card title={<Title level={4}>今日建议</Title>}
                  extra={<Button icon={<ReloadOutlined/>} onClick={() => setParams({})}>清空筛选</Button>}>
                <Space style={{marginBottom: 16}}>
                    <Text type="secondary">基金：</Text>
                    <Select showSearch optionFilterProp="label" placeholder="选择基金"
                            value={fundIdParam || undefined} style={{width: 280}}
                            options={fundOptions} allowClear
                            onChange={(v) => setParams(v ? {fundId: v} : {})}/>
                </Space>
                {fundId && todayError ? (
                    <QueryErrorState onRetry={refetchToday} description="今日建议加载失败"/>
                ) : fundId ? (
                    <Table rowKey="id" size="small" loading={todayLoading}
                           dataSource={todaySignal ? [todaySignal] : []}
                           columns={signalColumns(transactionColumn)} pagination={false}
                           locale={{emptyText: <EmptyState description="今日无建议"/>}}/>
                ) : (
                    <EmptyState description="选择基金查看今日建议"/>
                )}
            </Card>
            {fundId && (
                <Card title="历史建议查询">
                    <Space style={{marginBottom: 16}}>
                        <RangePicker value={range} onChange={setRange}/>
                    </Space>
                    {rangeError ? (
                        <QueryErrorState onRetry={refetchRange} description="历史建议加载失败"/>
                    ) : (
                        <Table rowKey="id" size="small" loading={rangeLoading} dataSource={rangeSignals}
                               columns={signalColumns(transactionColumn)} pagination={false}
                               locale={{emptyText: <EmptyState description="所选区间无建议"/>}}/>
                    )}
                </Card>
            )}
            <Modal title="采纳纪律建议" open={modal.open}
                   onCancel={() => setModal({open: false, signal: null})}
                   onOk={submit} confirmLoading={confirmOp.isPending}
                   okButtonProps={{disabled: isSell && !fundsReady}} destroyOnHidden>
                <Form form={form} layout="vertical">
                    {isSell ? (
                        <Form.Item label={isLogicBroken
                            ? `逻辑止损卖出份额（全仓，当前持仓 ${currentHoldingShares.toFixed(2)} 份）`
                            : `实际卖出份额（当前持仓 ${currentHoldingShares.toFixed(2)} 份）`}
                                   name="actualShares" rules={[
                                       {required: true, message: '请输入份额'},
                                       {validator: (_, value) => value <= currentHoldingShares
                                           ? Promise.resolve()
                                           : Promise.reject(new Error('卖出份额不能超过当前持仓'))},
                        ]}>
                            <InputNumber min={0.000001} max={currentHoldingShares || undefined}
                                         precision={6} className="full-width" disabled={isLogicBroken}/>
                        </Form.Item>
                    ) : (
                        <Form.Item label="实际下单金额" name="actualAmount"
                                   rules={[{required: true, message: '请输入金额'}]}>
                            <InputNumber min={0.01} precision={2} className="full-width" prefix="¥"/>
                        </Form.Item>
                    )}
                </Form>
            </Modal>
        </Space>
    );
}
