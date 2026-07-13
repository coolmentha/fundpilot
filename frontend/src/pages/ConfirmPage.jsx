import {useState} from 'react';
import {App, Button, Card, Form, InputNumber, Modal, Popconfirm, Space, Table, Typography} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';
import {Link} from 'react-router-dom';
import {useConfirmOperation, useFunds, useIgnoreSignal, usePendingSignals} from '../api/hooks.js';
import {datetime, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import QueryErrorState from '../components/QueryErrorState.jsx';
import {isQueryDataReady} from '../querySafety.js';

const {Title, Text} = Typography;

export default function ConfirmPage() {
    const {message} = App.useApp();
    const {data: signals, isLoading, isError, refetch} = usePendingSignals();
    const {
        data: funds,
        isLoading: fundsLoading,
        isError: fundsError,
        refetch: refetchFunds,
    } = useFunds();
    const [modal, setModal] = useState({open: false, signal: null});
    const [form] = Form.useForm();
    const confirmOp = useConfirmOperation(modal.signal?.fundId);
    const ignoreSignal = useIgnoreSignal();
    const fundsReady = isQueryDataReady({data: funds, isLoading: fundsLoading, isError: fundsError});

    const fundName = (id) => funds?.find((f) => f.id === id)?.fundName || `基金 #${id}`;
    const holdingShares = (id) => Number(funds?.find((f) => f.id === id)?.holdingShares || 0);

    const openConfirm = (signal) => {
        setModal({open: true, signal});
        const isSell = signal.signalType === 'SELL';
        const suggested = signal.suggestedMeasure?.value;
        const maxShares = holdingShares(signal.fundId);
        form.setFieldsValue({
            actualAmount: !isSell ? suggested : undefined,
            actualShares: isSell && maxShares > 0
                ? Math.min(Number(suggested || 0), maxShares)
                : undefined,
        });
    };
    const submit = async () => {
        if (modal.signal?.signalType === 'SELL' && !fundsReady) return;
        const values = await form.validateFields();
        await confirmOp.mutateAsync({
            signalLogId: modal.signal.id,
            actualAmount: values.actualAmount ?? null,
            actualShares: values.actualShares ?? null,
        });
        message.success('操作已确认，交易已生成（待净值确认）');
        setModal({open: false, signal: null});
    };
    const ignore = async (signal) => {
        await ignoreSignal.mutateAsync({fundId: signal.fundId, signalId: signal.id});
        message.success('信号已忽略');
    };

    const columns = [
        {title: '基金', width: 140, render: (_, r) => (
            <Link to={`/funds/${r.fundId}`}>{fundName(r.fundId)}</Link>
        )},
        {title: '类型', dataIndex: 'signalType', width: 90, render: (v) => <StatusTag value={v}/>},
        {title: '原因', dataIndex: 'reason', render: text},
        {title: '建议量', width: 120, render: (_, r) => {
            const m = r.suggestedMeasure;
            return m ? `${Number(m.value).toFixed(2)} (${text(m.measureUnit)})` : '-';
        }},
        {title: '信号时间', dataIndex: 'signalDate', width: 170, render: datetime},
        {
            title: '操作', width: 150, render: (_, r) => (
                <Space size="small">
                    <Button type="primary" size="small"
                            disabled={r.signalType === 'SELL' && !fundsReady}
                            onClick={() => openConfirm(r)}>确认</Button>
                    <Popconfirm title="忽略本次信号?" description="该信号会保留在历史记录中"
                                onConfirm={() => ignore(r)}>
                        <Button size="small" loading={ignoreSignal.isPending}>忽略</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const isSell = modal.signal?.signalType === 'SELL';
    const currentHoldingShares = holdingShares(modal.signal?.fundId);
    const suggestedShares = Number(modal.signal?.suggestedMeasure?.value || 0);

    return (
        <Space direction="vertical" size={16} className="full-width">
            <Card title={<Title level={4}>操作确认工作台</Title>} extra={
                <Button icon={<ReloadOutlined/>} onClick={() => refetch()}>刷新</Button>
            }>
                <Text type="secondary" style={{display: 'block', marginBottom: 16}}>
                    核对卖出建议后填写实际份额；不准备执行时可忽略，历史记录仍会保留。
                </Text>
                {fundsError && (
                    <QueryErrorState onRetry={refetchFunds} description="基金持仓加载失败，暂不可确认卖出"/>
                )}
                {isError ? <QueryErrorState onRetry={refetch} description="待确认信号加载失败"/> :
                    <Table rowKey="id" size="small" loading={isLoading} dataSource={signals}
                           columns={columns} pagination={false} scroll={{x: 900}}/>}
            </Card>
            <Modal title="确认信号操作" open={modal.open}
                   onCancel={() => setModal({open: false, signal: null})}
                   onOk={submit} confirmLoading={confirmOp.isPending}
                   okButtonProps={{disabled: isSell && !fundsReady}} destroyOnHidden>
                {modal.signal && (
                    <Space direction="vertical" style={{marginBottom: 16}}>
                        <Text>类型：<StatusTag value={modal.signal.signalType}/></Text>
                        {modal.signal.suggestedMeasure && (
                            <Text type="secondary">建议量：{Number(modal.signal.suggestedMeasure.value).toFixed(2)} ({text(modal.signal.suggestedMeasure.measureUnit)})</Text>
                        )}
                    </Space>
                )}
                <Form form={form} layout="vertical">
                    {!isSell && (
                        <Form.Item label="实际下单金额" name="actualAmount"
                                   rules={[{required: true, message: '请输入金额'}]}>
                            <InputNumber min={0.01} precision={2} className="full-width" prefix="¥"/>
                        </Form.Item>
                    )}
                    {isSell && (
                        <Form.Item label="实际卖出份额">
                            <Space direction="vertical" className="full-width" size="small">
                                <Text type="secondary">当前持仓：{currentHoldingShares.toFixed(2)} 份</Text>
                                <Form.Item name="actualShares" noStyle
                                           rules={[
                                               {required: true, message: '请输入份额'},
                                               {
                                                   validator: (_, value) => value <= currentHoldingShares
                                                       ? Promise.resolve()
                                                       : Promise.reject(new Error('卖出份额不能超过当前持仓')),
                                               },
                                           ]}>
                                    <InputNumber min={0.000001} max={currentHoldingShares || undefined}
                                                 precision={6} className="full-width"/>
                                </Form.Item>
                                <Space size="small">
                                    <Button size="small" disabled={!suggestedShares}
                                            onClick={() => form.setFieldValue('actualShares',
                                                Math.min(suggestedShares, currentHoldingShares))}>按建议</Button>
                                    <Button size="small" disabled={!currentHoldingShares}
                                            onClick={() => form.setFieldValue('actualShares', currentHoldingShares)}>全部卖出</Button>
                                </Space>
                            </Space>
                        </Form.Item>
                    )}
                </Form>
            </Modal>
        </Space>
    );
}
