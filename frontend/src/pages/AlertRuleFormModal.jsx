import {useEffect, useState} from 'react';
import {Alert, Button, Descriptions, Form, InputNumber, Modal, Radio, Select, Space, Steps, Typography} from 'antd';
import {labels} from '../constants.js';

const SCOPE_OPTIONS = [
    {value: 'GLOBAL', label: '全部关注基金'},
    {value: 'FUND', label: '指定基金'},
];

const RULE_TYPE_OPTIONS = [
    {value: 'RISE', label: '上涨提醒', description: '当日估值涨幅达到阈值时提醒'},
    {value: 'PROFIT', label: '盈利提醒', description: '持仓收益率达到阈值时提醒，仅对已持仓基金生效'},
    {value: 'DROP', label: '下跌提醒', description: '当日估值跌幅达到阈值时提醒'},
];

// 每一步需要校验的字段：范围（指定基金时必填）、提醒类型、阈值。
const STEP_FIELDS = [['scope', 'portfolioFundId'], ['ruleType'], ['threshold']];

export default function AlertRuleFormModal({open, editing, funds, onOk, onCancel, confirmLoading}) {
    const [form] = Form.useForm();
    const [step, setStep] = useState(0);
    const values = Form.useWatch([], form) ?? {};
    const scope = values.scope ?? 'GLOBAL';
    const ruleType = values.ruleType;
    const threshold = values.threshold;

    useEffect(() => {
        if (!open) return;
        if (editing) {
            form.setFieldsValue({
                scope: editing.scope,
                portfolioFundId: editing.portfolioFundId ?? undefined,
                ruleType: editing.ruleType,
                threshold: Number(editing.threshold) * 100,
            });
            return;
        }
        form.setFieldsValue({scope: 'GLOBAL', portfolioFundId: undefined, ruleType: undefined, threshold: undefined});
    }, [open, editing, form]);

    const next = () => form.validateFields(STEP_FIELDS[step]).then(() => setStep(step + 1)).catch(() => {});

    const handleFinish = (raw) => {
        onOk({
            scope: raw.scope,
            portfolioFundId: raw.scope === 'FUND' ? raw.portfolioFundId : null,
            ruleType: raw.ruleType,
            threshold: Number((Number(raw.threshold) / 100).toFixed(4)),
            enabled: editing ? editing.enabled : true,
        });
    };

    const selectedFund = (funds ?? []).find((fund) => fund.portfolioFundId === values.portfolioFundId);
    const footer = (
        <Space>
            <Button onClick={onCancel}>取消</Button>
            {step > 0 && <Button onClick={() => setStep(step - 1)}>上一步</Button>}
            {step < 2
                ? <Button type="primary" onClick={next}>下一步</Button>
                : <Button type="primary" loading={confirmLoading} onClick={() => form.submit()}>
                    {editing ? '保存' : '创建'}
                </Button>}
        </Space>
    );

    return (
        <Modal className="alert-rule-form-modal" title={editing ? '编辑提醒规则' : '新建提醒规则'} open={open}
               onCancel={onCancel} footer={footer} destroyOnHidden width={560}>
            <Steps className="alert-rule-wizard-steps" current={step} size="small" responsive={false}
                   items={[{title: '选择范围'}, {title: '提醒类型'}, {title: '设置阈值'}]}/>
            <Form form={form} layout="vertical" initialValues={{scope: 'GLOBAL'}} onFinish={handleFinish}>
                <div className="alert-rule-step" style={{display: step === 0 ? 'block' : 'none'}}>
                    <Form.Item label="提醒范围" name="scope" rules={[{required: true, message: '请选择提醒范围'}]}>
                        <Radio.Group options={SCOPE_OPTIONS}/>
                    </Form.Item>
                    {scope === 'FUND' && (
                        <Form.Item label="选择基金" name="portfolioFundId" rules={[{required: true, message: '请选择基金'}]}>
                            <Select showSearch optionFilterProp="label" placeholder="请选择基金"
                                    options={(funds ?? []).map((fund) => ({
                                        value: fund.portfolioFundId,
                                        label: `${fund.fundName} ${fund.fundCode}`,
                                    }))}/>
                        </Form.Item>
                    )}
                    {scope === 'GLOBAL' && (
                        <Alert type="info" showIcon message="若某只基金已配置同类型的单基金规则，该基金将按单基金规则提醒"/>
                    )}
                </div>
                <div className="alert-rule-step" style={{display: step === 1 ? 'block' : 'none'}}>
                    <Form.Item label="提醒类型" name="ruleType" rules={[{required: true, message: '请选择提醒类型'}]}>
                        <Radio.Group className="alert-rule-type-group">
                            <Space direction="vertical">
                                {RULE_TYPE_OPTIONS.map((option) => (
                                    <Radio key={option.value} value={option.value}>
                                        <span>{option.label}</span>
                                        <Typography.Text type="secondary" className="alert-rule-type-hint">
                                            {option.description}
                                        </Typography.Text>
                                    </Radio>
                                ))}
                            </Space>
                        </Radio.Group>
                    </Form.Item>
                    {ruleType === 'PROFIT' && (
                        <Alert type="warning" showIcon message="盈利提醒仅对已持仓基金生效，已清仓基金将被跳过"/>
                    )}
                </div>
                <div className="alert-rule-step" style={{display: step === 2 ? 'block' : 'none'}}>
                    <Form.Item label="触发阈值" name="threshold"
                               rules={[
                                   {required: true, message: '请填写触发阈值'},
                                   {type: 'number', min: 0.01, max: 100, message: '请输入 0.01% - 100%'},
                               ]}>
                        <InputNumber step={0.5} precision={2} min={0.01} max={100} addonAfter="%" className="full-width"/>
                    </Form.Item>
                    <Descriptions className="alert-rule-summary" size="small" column={1} bordered>
                        <Descriptions.Item label="范围">
                            {labels[scope] || '-'}{scope === 'FUND' && selectedFund ? ` · ${selectedFund.fundName}` : ''}
                        </Descriptions.Item>
                        <Descriptions.Item label="类型">{labels[ruleType] || '-'}</Descriptions.Item>
                        <Descriptions.Item label="阈值">{threshold ? `${threshold}%` : '-'}</Descriptions.Item>
                    </Descriptions>
                    <Typography.Text type="secondary">每个交易日 14:30 评估一次，同一规则当天最多提醒一次</Typography.Text>
                </div>
            </Form>
        </Modal>
    );
}
