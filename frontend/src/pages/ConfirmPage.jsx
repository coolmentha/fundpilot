import {useState} from 'react';
import {App, Button, Card, Form, InputNumber, Modal, Space, Table, Typography} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';
import {useConfirmOperation, useFunds, usePendingSignals} from '../api/hooks.js';
import {datetime, money, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';

const {Title, Text} = Typography;

export default function ConfirmPage() {
    const {message} = App.useApp();
    const {data: signals, isLoading, refetch} = usePendingSignals();
    const {data: funds} = useFunds();
    const [modal, setModal] = useState({open: false, signal: null});
    const [form] = Form.useForm();
    const confirmOp = useConfirmOperation(modal.signal?.fundId);

    const fundName = (id) => funds?.find((f) => f.id === id)?.fundName || `基金 #${id}`;

    const openConfirm = (signal) => {
        setModal({open: true, signal});
        const isSell = signal.signalType === 'SELL';
        const suggested = signal.suggestedMeasure?.value;
        form.setFieldsValue({
            actualAmount: !isSell ? suggested : undefined,
            actualShares: isSell ? suggested : undefined,
        });
    };
    const submit = async () => {
        const values = await form.validateFields();
        await confirmOp.mutateAsync({
            signalLogId: modal.signal.id,
            actualAmount: values.actualAmount ?? null,
            actualShares: values.actualShares ?? null,
        });
        message.success('操作已确认，交易已生成（待净值确认）');
        setModal({open: false, signal: null});
    };

    const columns = [
        {title: '基金', width: 140, render: (_, r) => fundName(r.fundId)},
        {title: '类型', dataIndex: 'signalType', width: 90, render: (v) => <StatusTag value={v}/>},
        {title: '档位', dataIndex: 'triggerTier', width: 70, render: (v) => v ?? '-'},
        {title: '原因', dataIndex: 'reason', render: text},
        {title: '建议量', width: 120, render: (_, r) => {
            const m = r.suggestedMeasure;
            return m ? `${Number(m.value).toFixed(2)} (${text(m.measureUnit)})` : '-';
        }},
        {title: '信号时间', dataIndex: 'signalDate', width: 170, render: datetime},
        {
            title: '操作', width: 110, render: (_, r) => r.signalType !== 'NONE' && (
                <Button type="primary" size="small" onClick={() => openConfirm(r)}>确认操作</Button>
            ),
        },
    ];

    const isSell = modal.signal?.signalType === 'SELL';

    return (
        <Space direction="vertical" size={16} className="full-width">
            <Card title={<Title level={4}>操作确认工作台</Title>} extra={
                <Button icon={<ReloadOutlined/>} onClick={() => refetch()}>刷新</Button>
            }>
                <Text type="secondary" style={{display: 'block', marginBottom: 16}}>
                    对未回应信号执行确认：BUILD/ADD 填实际下单金额，SELL 填实际卖出份额。override 不留痕，仅存实际值。
                </Text>
                <Table rowKey="id" size="small" loading={isLoading} dataSource={signals}
                       columns={columns} pagination={false} scroll={{x: 1000}}/>
            </Card>
            <Modal title="确认信号操作" open={modal.open}
                   onCancel={() => setModal({open: false, signal: null})}
                   onOk={submit} confirmLoading={confirmOp.isPending} destroyOnHidden>
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
                        <Form.Item label="实际卖出份额" name="actualShares"
                                   rules={[{required: true, message: '请输入份额'}]}>
                            <InputNumber min={0.000001} precision={6} className="full-width"/>
                        </Form.Item>
                    )}
                </Form>
            </Modal>
        </Space>
    );
}
