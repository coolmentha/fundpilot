import {App, Button, DatePicker, Form, InputNumber, Modal, Space} from 'antd';
import dayjs from 'dayjs';
import {useUpdateTransaction} from '../api/hooks.js';
import {BUY_SOURCES, pendingTransactionBody} from '../transactionEditing.js';

export default function PendingTransactionEditModal({transaction, holdingShares, onClose}) {
    const {message} = App.useApp();
    const [form] = Form.useForm();
    const update = useUpdateTransaction();
    const isBuy = BUY_SOURCES.has(transaction.source);

    const submit = async () => {
        const values = await form.validateFields();
        await update.mutateAsync({id: transaction.id, body: pendingTransactionBody(transaction.source, values)});
        message.success('待确认流水已更新');
        onClose();
    };

    return (
        <Modal title="编辑待确认流水" open onCancel={onClose} onOk={submit}
               okButtonProps={{loading: update.isPending}} destroyOnClose>
            <Form form={form} layout="vertical" initialValues={{
                amount: transaction.amount,
                shares: transaction.shares,
                tradeDate: dayjs(transaction.tradeDate),
            }}>
                <Form.Item label="交易发生日" name="tradeDate"
                           rules={[{required: true, message: '请选择交易发生日'}]}>
                    <DatePicker className="full-width"
                                disabledDate={(date) => date && date.isAfter(dayjs().endOf('day'))}/>
                </Form.Item>
                {isBuy ? (
                    <Form.Item label="金额(元)" name="amount"
                               rules={[{required: true, message: '买入类需填金额'}]}>
                        <InputNumber className="full-width" min={0.01} step={100} precision={2} prefix="¥"/>
                    </Form.Item>
                ) : (
                    <Form.Item label="份额" required
                               extra={holdingShares == null ? null : `当前可用 ${Number(holdingShares).toFixed(2)} 份`}>
                        <Space.Compact block>
                            <Form.Item name="shares" noStyle
                                       rules={[{required: true, message: '卖出类需填份额'}]}>
                                <InputNumber className="full-width" min={0.01} step={0.01} precision={2}
                                             />
                            </Form.Item>
                            <Button disabled={holdingShares == null}
                                    onClick={() => {
                                        form.setFieldValue('shares', Number(holdingShares).toFixed(2));
                                    }}>全部</Button>
                        </Space.Compact>
                    </Form.Item>
                )}
            </Form>
        </Modal>
    );
}
