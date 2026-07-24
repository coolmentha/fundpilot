import {useState} from 'react';
import {Card, Table, Tooltip, Typography, Button, Popconfirm, Modal, Form, InputNumber, Select, Alert, Space, DatePicker} from 'antd';
import {InfoCircleOutlined, PlusOutlined} from '@ant-design/icons';
import dayjs from 'dayjs';
import {useFundTransactions, useCancelTransaction, useCreateManualTransaction, useConfirmTransaction, useFunds, useFundFeeRates} from '../api/hooks.js';
import {datetime, money, fundSourceOptions} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';
import PendingTransactionEditModal from '../components/PendingTransactionEditModal.jsx';
import {adjustmentFromTarget, canEditPendingTransaction} from '../transactionEditing.js';
import {redemptionLadderText} from '../feeRates.js';

const {Title} = Typography;

// 买入类写金额,卖出类写份额(与后端 createManual 方向一致)
const SELL_SOURCES = new Set(['DECREASE', 'TRANSFER_OUT']);
const ADJUST_SOURCES = new Set(['ADJUST_IN', 'ADJUST_OUT']);
const ADJUST_TARGET_SOURCE = 'ADJUST_TARGET';
const transactionSourceOptions = [
    ...fundSourceOptions.filter((option) => !ADJUST_SOURCES.has(option.value)),
    {value: ADJUST_TARGET_SOURCE, label: '调整持仓'},
];

/**
 * 基金详情 · 交易流水 tab(issue #18 交易合并到基金详情 + 手动录入)。
 * 列出该基金全部交易(按时间倒序),PENDING 行内嵌撤单;"手动录入"弹窗支持七类来源,
 * 买入类填金额、卖出类填份额(份额/金额等净值确认后回填),手动卖出不卡 7 天硬约束。
 */
export default function FundTransactionTab({fundId}) {
    const {data: transactions, isLoading} = useFundTransactions(fundId);
    const {data: funds} = useFunds();
    const cancelTx = useCancelTransaction();
    const confirmTx = useConfirmTransaction();
    const createManual = useCreateManualTransaction(fundId);
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [adjustmentConfirmation, setAdjustmentConfirmation] = useState(null);
    const [form] = Form.useForm();
    const source = Form.useWatch('source', form);
    const isSell = source && SELL_SOURCES.has(source);
    const isAdjustTarget = source === ADJUST_TARGET_SOURCE;
    const isTransferOut = source === 'TRANSFER_OUT';
    const currentFund = funds?.find((fund) => fund.id === fundId);
    const currentHoldingShares = Number(currentFund?.holdingShares ?? 0);
    const {data: feeRates} = useFundFeeRates(fundId);
    const redemptionHint = redemptionLadderText(feeRates?.redemptionLadder);

    const columns = [
        {title: '交易日期', dataIndex: 'tradeDate', width: 170, render: datetime},
        {title: '来源', dataIndex: 'source', width: 110, render: (v) => <StatusTag value={v}/>},
        {title: '金额', dataIndex: 'amount', width: 140, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{money(v)}</span>},
        {title: '份额', dataIndex: 'shares', width: 120, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{Number(v).toFixed(2)}</span>},
        {title: '净值', dataIndex: 'nav', width: 100, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{Number(v).toFixed(4)}</span>},
        {title: '手续费', dataIndex: 'fee', width: 110, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{money(v)}</span>},
        {title: <Tooltip title="PENDING 表示等待净值确认；CONFIRMED 表示已进入账本"><span>状态 <InfoCircleOutlined /></span></Tooltip>, dataIndex: 'status', width: 110, render: (v) => <StatusTag value={v}/>},
        {
            title: '', width: 180, render: (_, r) => r.status === 'PENDING' && (
                <Space size={0}>
                    {canEditPendingTransaction(r) && (
                        <Button type="link" size="small" onClick={() => setEditing(r)}>编辑</Button>
                    )}
                    <Popconfirm title="确认该笔交易?" description="用交易发生日净值回填另一侧并转 CONFIRMED"
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

    const createTransaction = async (body) => {
        await createManual.mutateAsync(body);
        setAdjustmentConfirmation(null);
        setOpen(false);
        form.resetFields();
    };

    const submit = async () => {
        const values = await form.validateFields();
        const tradeDate = values.tradeDate
            ? `${values.tradeDate.format('YYYY-MM-DD')}T00:00:00+08:00`
            : null;
        if (isAdjustTarget) {
            const adjustment = adjustmentFromTarget(currentHoldingShares, values.targetShares);
            if (!adjustment) {
                form.setFields([{name: 'targetShares', errors: ['目标持仓与当前持仓相同，无需调整']}]);
                return;
            }
            setAdjustmentConfirmation({
                ...adjustment,
                currentShares: currentHoldingShares,
                targetShares: Number(values.targetShares),
                tradeDate,
            });
            return;
        }
        const body = {source: values.source, tradeDate};
        if (SELL_SOURCES.has(values.source)) {
            body.shares = values.shares;
        } else {
            body.amount = values.amount;
        }
        // 基金转换(task 07-08):转出时选了转入基金,带 targetFundId 让后端建两条互指交易
        if (values.source === 'TRANSFER_OUT' && values.targetFundId) {
            body.targetFundId = values.targetFundId;
        }
        await createTransaction(body);
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
                <Form form={form} layout="vertical" initialValues={{source: 'INCREASE', tradeDate: dayjs()}}>
                    <Form.Item label="来源" name="source" rules={[{required: true}]}>
                        <Select options={transactionSourceOptions}/>
                    </Form.Item>
                    <Form.Item label="交易发生日" name="tradeDate"
                               rules={[{required: true, message: '请选择交易发生日'}]}>
                        <DatePicker className="full-width"
                                    disabledDate={(date) => date && date.isAfter(dayjs().endOf('day'))}/>
                    </Form.Item>
                    {isAdjustTarget ? (
                        <Form.Item label="调整后持仓份额" name="targetShares"
                                   extra={`当前持仓 ${currentHoldingShares.toFixed(2)} 份；系统将自动判断调增或调减`}
                                   rules={[{required: true, message: '请输入调整后持仓份额'}]}>
                            <InputNumber className="full-width" min={0} step={0.01} precision={2}/>
                        </Form.Item>
                    ) : isSell ? (
                        <Form.Item label="份额" required
                                   extra={currentFund?.holdingShares == null ? null : `当前可用 ${Number(currentFund.holdingShares).toFixed(2)} 份`}>
                            <Space.Compact block>
                                <Form.Item name="shares" noStyle rules={[{required: true, message: '卖出类需填份额'}]}>
                                    <InputNumber className="full-width" min={0.01} step={0.01} precision={2}/>
                                </Form.Item>
                                <Button disabled={currentFund?.holdingShares == null}
                                        onClick={() => {
                                            form.setFieldValue('shares', Number(currentFund.holdingShares).toFixed(2));
                                        }}>
                                    全部
                                </Button>
                            </Space.Compact>
                        </Form.Item>
                    ) : (
                        <Form.Item label="金额(元)" name="amount" rules={[{required: true, message: '买入类需填金额'}]}>
                            <InputNumber className="full-width" min={0.01} step={100} precision={2} prefix="¥"/>
                        </Form.Item>
                    )}
                    {isTransferOut && (
                        <Form.Item label="转入基金" name="targetFundId"
                                   extra="选填:选了即基金转换(转出A份额->转入B份额),确认时自动算份额与手续费;不选为纯转出单条记录">
                            <Select allowClear showSearch optionFilterProp="label"
                                    placeholder="选择转入基金(留空为纯转出)"
                                    options={(funds ?? []).filter(f => f.id !== fundId)
                                        .map(f => ({value: f.id, label: `${f.fundName}(${f.fundCode})`}))}/>
                        </Form.Item>
                    )}
                    {isSell && (
                        <Alert type="info" showIcon
                               message={redemptionHint
                                   ? `赎回费参考：${redemptionHint}`
                                   : '赎回费率未获取，确认时按可用费率降级计算。'}
                               description="最终按 FIFO 持有天数和交易发生日净值确认，当前录入份额不含手续费预扣。"/>
                    )}
                </Form>
            </Modal>
            <Modal title="确认调整持仓" open={!!adjustmentConfirmation}
                   onCancel={() => setAdjustmentConfirmation(null)}
                   onOk={() => createTransaction({
                       source: adjustmentConfirmation.source,
                       shares: adjustmentConfirmation.shares,
                       tradeDate: adjustmentConfirmation.tradeDate,
                   })}
                   okText="确认调整" okButtonProps={{loading: createManual.isPending}}>
                {adjustmentConfirmation && (
                    <Space direction="vertical" size={4}>
                        <span>当前持仓：{adjustmentConfirmation.currentShares.toFixed(2)} 份</span>
                        <span>调整后持仓：{adjustmentConfirmation.targetShares.toFixed(2)} 份</span>
                        <span>{adjustmentConfirmation.source === 'ADJUST_IN' ? '调增' : '调减'}：{adjustmentConfirmation.shares.toFixed(2)} 份</span>
                        <Alert type="warning" showIcon message="确认后立即修正持仓份额，不计算净值、手续费或交易批次。"/>
                    </Space>
                )}
            </Modal>
            {editing && (
                <PendingTransactionEditModal transaction={editing}
                                             holdingShares={currentFund?.holdingShares}
                                             onClose={() => setEditing(null)}/>
            )}
        </Card>
    );
}
