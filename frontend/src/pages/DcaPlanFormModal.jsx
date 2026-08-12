import {useEffect} from 'react';
import {Form, Input, InputNumber, Modal, Segmented, Select, Switch, Typography} from 'antd';

const FREQUENCY_OPTIONS = [
    {value: 'DAILY', label: '日定投'},
    {value: 'WEEKLY', label: '周定投'},
    {value: 'MONTHLY', label: '月定投'},
];
const WEEK_OPTIONS = [
    {value: 1, label: '周一'}, {value: 2, label: '周二'}, {value: 3, label: '周三'},
    {value: 4, label: '周四'}, {value: 5, label: '周五'},
];
const STRATEGY_OPTIONS = [
    {value: 'FIXED', label: '固定'},
    {value: 'LOW_VALUATION', label: '低估'},
    {value: 'MOVING_AVERAGE', label: '均线'},
    {value: 'CHANGE_RATE', label: '涨跌幅'},
];
const MA_OPTIONS = [180, 250, 500].map(value => ({value, label: `${value} 日均线`}));

export default function DcaPlanFormModal({open, editing, benchmarkIndexCode, onOk, onCancel, confirmLoading}) {
    const [form] = Form.useForm();
    const frequency = Form.useWatch('frequency', form);
    const amountStrategy = Form.useWatch('amountStrategy', form);
    const strategyOptions = STRATEGY_OPTIONS.map(option => option.value === 'LOW_VALUATION'
        ? {...option, disabled: !benchmarkIndexCode && editing?.amountStrategy !== 'LOW_VALUATION'}
        : option);

    useEffect(() => {
        if (open) {
            form.setFieldsValue({
                enabled: editing?.enabled ?? true,
                amount: editing?.amount ?? 1000,
                frequency: editing?.frequency ?? 'WEEKLY',
                dayOfWeek: editing?.dayOfWeek ?? 1,
                dayOfMonth: editing?.dayOfMonth ?? 1,
                amountStrategy: editing?.amountStrategy ?? 'FIXED',
                referenceIndexCode: editing?.referenceIndexCode ?? benchmarkIndexCode ?? undefined,
                movingAverageDays: editing?.movingAverageDays ?? 250,
            });
        }
    }, [open, editing, benchmarkIndexCode, form]);

    const handleOk = async () => {
        const values = await form.validateFields();
        onOk({
            ...values,
            referenceIndexCode: amountStrategy === 'MOVING_AVERAGE' ? values.referenceIndexCode : null,
            movingAverageDays: amountStrategy === 'MOVING_AVERAGE' ? values.movingAverageDays : null,
        });
    };

    return (
        <Modal title={editing ? '编辑定投计划' : '新建定投计划'} open={open} onCancel={onCancel}
               onOk={handleOk} confirmLoading={confirmLoading} destroyOnHidden width={520}>
            <Form form={form} layout="vertical">
                <Form.Item label="启用自动定投" name="enabled" valuePropName="checked">
                    <Switch/>
                </Form.Item>
                <Form.Item label="每次金额(元)" name="amount"
                           rules={[{required: true, message: '请输入金额'}]}>
                    <InputNumber min={1} precision={2} className="full-width" placeholder="如 1000"/>
                </Form.Item>
                <Form.Item label="金额模式" name="amountStrategy"
                           rules={[{required: true, message: '请选择金额模式'}]}>
                    <Segmented block options={strategyOptions}/>
                </Form.Item>
                {amountStrategy === 'LOW_VALUATION' && (
                    <Typography.Text type="secondary">
                        仅在基金基准指数处于历史估值 30% 分位及以下时执行，否则本期跳过。
                    </Typography.Text>
                )}
                {amountStrategy === 'MOVING_AVERAGE' && (
                    <>
                        <Form.Item label="参考指数代码" name="referenceIndexCode"
                                   rules={[{required: true, message: '请输入参考指数代码'}]}>
                            <Input placeholder="如 000300.SH，默认使用基金基准指数"/>
                        </Form.Item>
                        <Form.Item label="均线周期" name="movingAverageDays"
                                   rules={[{required: true, message: '请选择均线周期'}]}>
                            <Select options={MA_OPTIONS}/>
                        </Form.Item>
                    </>
                )}
                <Form.Item label="频率" name="frequency"
                           rules={[{required: true, message: '请选择频率'}]}>
                    <Select options={FREQUENCY_OPTIONS}/>
                </Form.Item>
                {frequency === 'WEEKLY' && (
                    <Form.Item label="定投日" name="dayOfWeek"
                               rules={[{required: true, message: '请选择定投日'}]}>
                        <Select options={WEEK_OPTIONS}/>
                    </Form.Item>
                )}
                {frequency === 'MONTHLY' && (
                    <Form.Item label="定投日(每月几号,1-28)" name="dayOfMonth"
                               rules={[{required: true, message: '请输入定投日'}]}>
                        <InputNumber min={1} max={28} className="full-width" placeholder="如 15"/>
                    </Form.Item>
                )}
            </Form>
        </Modal>
    );
}
