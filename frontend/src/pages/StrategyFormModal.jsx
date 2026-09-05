import {useEffect, useMemo, useRef} from 'react';
import {Button, Descriptions, Form, InputNumber, Modal, Space, Tag} from 'antd';
import {UndoOutlined} from '@ant-design/icons';
import {labels} from '../constants.js';

const FIELDS = [
    {key: 'profitActivationPercent', label: '止盈启动收益率', min: 0.01, max: 99.99},
    {key: 'stopLossPullbackPercent', label: '高点回撤确认', min: 0.01, max: 99.99},
    {key: 'profitHarvestPercent', label: '浮盈收割比例', min: 0.01, max: 100},
    {key: 'minimumHoldingPercent', label: '最低保留仓位', min: 0, max: 99.99},
    {key: 'maxSingleSellPercent', label: '单次最大卖出', min: 0.01, max: 100},
];

const toPercentValues = (source) => {
    if (!source) return null;
    const values = {};
    FIELDS.forEach(({key}) => {
        values[key] = Number(source[key]) * 100;
    });
    values.cooldownTradingDays = source.cooldownTradingDays;
    return values;
};

const toPayload = (values, recommendation, customized) => {
    const payload = {};
    FIELDS.forEach(({key}) => {
        payload[key] = Number((Number(values[key]) / 100).toFixed(4));
    });
    payload.cooldownTradingDays = values.cooldownTradingDays;
    payload.presetFundCategory = recommendation?.fundCategory ?? null;
    payload.presetVersion = recommendation?.presetVersion ?? null;
    payload.customized = customized;
    return payload;
};

export default function StrategyFormModal({open, editing, recommendation, onOk, onCancel, confirmLoading}) {
    const [form] = Form.useForm();
    const initialized = useRef(false);

    useEffect(() => {
        if (!open) {
            initialized.current = false;
            form.resetFields();
            return;
        }
        const source = editing || recommendation;
        if (!initialized.current && source) {
            form.setFieldsValue(toPercentValues(source));
            initialized.current = true;
        }
    }, [open, editing, recommendation, form]);

    const current = Form.useWatch([], form);
    const recommendedFormValues = useMemo(() => toPercentValues(recommendation), [recommendation]);
    const customized = useMemo(() => {
        if (!current || !recommendedFormValues) return false;
        return [...FIELDS.map(({key}) => key), 'cooldownTradingDays']
            .some((key) => Number(current[key]) !== Number(recommendedFormValues[key]));
    }, [current, recommendedFormValues]);

    const handleOk = () => form.submit();

    const restoreRecommendation = () => {
        if (recommendedFormValues) form.setFieldsValue(recommendedFormValues);
    };

    return (
        <Modal title={editing ? '编辑定投止盈策略' : '新建定投止盈策略'} open={open} onCancel={onCancel}
               onOk={handleOk} confirmLoading={confirmLoading} destroyOnHidden width={640}
               styles={{body: {maxHeight: 'calc(100vh - 230px)', overflowY: 'auto', paddingRight: 4}}}>
            <Form form={form} layout="vertical"
                  onFinish={(values) => onOk(toPayload(values, recommendation, customized))}>
                <Space className="full-width" style={{justifyContent: 'space-between', marginBottom: 12}}>
                    <Space>
                        <Tag color="blue">{labels[recommendation?.fundCategory] || '-'}</Tag>
                        <Tag color={customized ? 'gold' : 'green'}>{customized ? '已自定义' : '类型推荐'}</Tag>
                    </Space>
                    <Button icon={<UndoOutlined/>} onClick={restoreRecommendation}
                            disabled={!recommendation}>恢复推荐值</Button>
                </Space>
                <div className="strategy-form-grid">
                    {FIELDS.map((field) => (
                        <Form.Item key={field.key} label={field.label} name={field.key}
                                   rules={[
                                       {required: true, message: '请填写'},
                                       {type: 'number', min: field.min, max: field.max,
                                           message: `请输入 ${field.min}% - ${field.max}%`},
                                   ]}>
                            <InputNumber step={0.5} precision={2} addonAfter="%" className="full-width"/>
                        </Form.Item>
                    ))}
                    <Form.Item label="止盈后冷静期" name="cooldownTradingDays"
                               rules={[{required: true, message: '请填写'}, {
                                   type: 'number', min: 0, max: 250, message: '请输入 0 - 250 个交易日',
                               }]}>
                        <InputNumber step={1} precision={0} addonAfter="交易日" className="full-width"/>
                    </Form.Item>
                </div>
                <Descriptions size="small" column={2} bordered>
                    <Descriptions.Item label="启动">整体收益达到 {current?.profitActivationPercent ?? '-'}%</Descriptions.Item>
                    <Descriptions.Item label="确认">周期高点回撤 {current?.stopLossPullbackPercent ?? '-'}%</Descriptions.Item>
                    <Descriptions.Item label="收割">浮盈的 {current?.profitHarvestPercent ?? '-'}%</Descriptions.Item>
                    <Descriptions.Item label="保留">至少保留 {current?.minimumHoldingPercent ?? '-'}% 仓位</Descriptions.Item>
                </Descriptions>
            </Form>
        </Modal>
    );
}
