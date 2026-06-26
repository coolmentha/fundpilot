import {useEffect} from 'react';
import {Form, InputNumber, Modal} from 'antd';

// StrategyConfigRequest 的 10 个字段。回撤阈值是负数（如 -0.08），比例是正小数（如 0.30）。
const FIELDS = [
    {key: 'tier1Drawdown', label: '一档回撤', placeholder: -0.08},
    {key: 'tier2Drawdown', label: '二档回撤', placeholder: -0.16},
    {key: 'tier3Drawdown', label: '三档回撤', placeholder: -0.25},
    {key: 'tier4Drawdown', label: '四档回撤', placeholder: -0.35},
    {key: 'tier1Ratio', label: '一档加仓比例', placeholder: 0.30},
    {key: 'tier2Ratio', label: '二档加仓比例', placeholder: 0.30},
    {key: 'tier3Ratio', label: '三档加仓比例', placeholder: 0.20},
    {key: 'tier4Ratio', label: '四档加仓比例', placeholder: 0.20},
    {key: 'weeklyCoolDownThreshold', label: '周跌幅冷静阈值', placeholder: -0.08},
    {key: 'stopLossPullbackPercent', label: '移动止盈回落', placeholder: -0.08},
];

export default function StrategyFormModal({open, editing, onOk, onCancel, confirmLoading}) {
    const [form] = Form.useForm();
    useEffect(() => {
        if (open) {
            const values = {};
            FIELDS.forEach((f) => {
                values[f.key] = editing?.[f.key] ?? f.placeholder;
            });
            form.setFieldsValue(values);
        }
    }, [open, editing, form]);

    const handleOk = async () => {
        const values = await form.validateFields();
        onOk(values);
    };

    return (
        <Modal title={editing ? '编辑策略参数' : '新建策略参数'} open={open} onCancel={onCancel}
               onOk={handleOk} confirmLoading={confirmLoading} destroyOnHidden width={620}>
            <Form form={form} layout="vertical">
                <p style={{color: '#888', fontSize: 12}}>
                    回撤阈值填负数（如 -0.08 表示跌 8%）；加仓比例填正小数（如 0.30 表示 30%）。
                </p>
                <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 16px'}}>
                    {FIELDS.map((f) => (
                        <Form.Item key={f.key} label={f.label} name={f.key}
                                   rules={[{required: true, message: '请填写'}]}>
                            <InputNumber step={0.01} precision={4} className="full-width"
                                         placeholder={String(f.placeholder)}/>
                        </Form.Item>
                    ))}
                </div>
            </Form>
        </Modal>
    );
}
