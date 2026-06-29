import {useState} from 'react';
import {Card, Table, Typography, Button, Popconfirm, Modal, Form, InputNumber, Select, Alert, Space} from 'antd';
import {PlusOutlined} from '@ant-design/icons';
import {useFundTransactions, useCancelTransaction, useCreateManualTransaction, useConfirmTransaction} from '../api/hooks.js';
import {datetime, money, fundSourceOptions} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';

const {Title} = Typography;

// 买入类写金额,卖出类写份额(与后端 createManual 方向一致)
const SELL_SOURCES = new Set(['DECREASE', 'TRANSFER_OUT']);

/**
 * 基金详情 · 交易流水 tab(issue #18 交易合并到基金详情 + 手动录入)。
 * 列出该基金全部交易(按时间倒序),PENDING 行内嵌撤单;"手动录入"弹窗支持五类来源,
 * 买入类填金额、卖出类填份额(份额/金额等净值确认后回填),手动卖出不卡 7 天硬约束。
 */
export default function FundTransactionTab({fundId}) {
    const {data: transactions, isLoading} = useFundTransactions(fundId);
    const cancelTx = useCancelTransaction();
    const confirmTx = useConfirmTransaction();
    const createManual = useCreateManualTransaction(fundId);
    const [open, setOpen] = useState(false);
    const [form] = Form.useForm();
    const source = Form.useWatch('source', form);
    const isSell = source && SELL_SOURCES.has(source);

    const columns = [
        {title: '日期', dataIndex: 'createdDate', width: 170, render: datetime},
        {title: '来源', dataIndex: 'source', width: 110, render: (v) => <StatusTag value={v}/>},
        {title: '金额', dataIndex: 'amount', width: 140, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{money(v)}</span>},
        {title: '份额', dataIndex: 'shares', width: 120, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{Number(v).toFixed(2)}</span>},
        {title: '净值', dataIndex: 'nav', width: 100, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{Number(v).toFixed(4)}</span>},
        {title: '状态', dataIndex: 'status', width: 110, render: (v) => <StatusTag value={v}/>},
        {
            title: '', width: 130, render: (_, r) => r.status === 'PENDING' && (
                <Space size={0}>
                    <Popconfirm title="确认该笔交易?" description="用最新净值回填另一侧并转 CONFIRMED"
                                onConfirm={() => confirmTx.mutate(r.id)}>
                        <Button type="link" size="small" loading={confirmTx.isPending}>确认</Button>
                    </Popconfirm>
                    <Popconfirm title="撤单该笔交易?" onConfirm={() => cancelTx.mutate(r.id)}>
                        <Button type="link" size="small" danger loading={cancelTx.isPending}>撤单</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const submit = async () => {
        const values = await form.validateFields();
        const body = {source: values.source};
        if (SELL_SOURCES.has(values.source)) {
            body.shares = values.shares;
        } else {
            body.amount = values.amount;
        }
        await createManual.mutateAsync(body);
        setOpen(false);
        form.resetFields();
    };

    return (
        <Card title={<Title level={5}>交易流水</Title>}
              extra={<Button type="primary" icon={<PlusOutlined/>} onClick={() => setOpen(true)}>手动录入</Button>}>
            <Table rowKey="id" size="small" loading={isLoading} dataSource={transactions}
                   columns={columns} pagination={{pageSize: 10, size: 'small'}} scroll={{x: 820}}
                   locale={{emptyText: <EmptyState description="暂无交易"/>}}/>

            <Modal title="手动录入交易" open={open} onCancel={() => setOpen(false)}
                   onOk={submit} okButtonProps={{loading: createManual.isPending}}
                   destroyOnClose onClose={() => form.resetFields()}>
                <Form form={form} layout="vertical" initialValues={{source: 'INCREASE'}}>
                    <Form.Item label="来源" name="source" rules={[{required: true}]}>
                        <Select options={fundSourceOptions}/>
                    </Form.Item>
                    {isSell ? (
                        <Form.Item label="份额" name="shares" rules={[{required: true, message: '卖出类需填份额'}]}>
                            <InputNumber className="full-width" step={0.01} precision={2}/>
                        </Form.Item>
                    ) : (
                        <Form.Item label="金额(元)" name="amount" rules={[{required: true, message: '买入类需填金额'}]}>
                            <InputNumber className="full-width" step={100} precision={2} prefix="¥"/>
                        </Form.Item>
                    )}
                    {isSell && (
                        <Alert type="info" showIcon
                               message="手动卖出不卡 7 天硬约束,可自行减仓;份额对应的金额等当晚净值确认后回填。"/>
                    )}
                </Form>
            </Modal>
        </Card>
    );
}
