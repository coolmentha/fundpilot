import {useEffect} from 'react';
import {Form, InputNumber, Modal} from 'antd';

// 金字塔退场后,策略参数只剩移动止盈回落幅度(回落 n×本阈值卖 holdingShares×n/4)。
const FIELDS = [
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
               onOk={handleOk} confirmLoading={confirmLoading} destroyOnHidden width={520}>
            <Form form={form} layout="vertical">
                <p style={{color: '#888', fontSize: 12}}>
                    移动止盈回落填负数（如 -0.08 表示从持有期高点回落 8% 触发卖 1/4,16% 卖 1/2,以此类推）。
                </p>
                {FIELDS.map((f) => (
                    <Form.Item key={f.key} label={f.label} name={f.key}
                               rules={[{required: true, message: '请填写'}]}>
                        <InputNumber step={0.01} precision={4} className="full-width"
                                     placeholder={String(f.placeholder)}/>
                    </Form.Item>
                ))}
            </Form>
        </Modal>
    );
}
