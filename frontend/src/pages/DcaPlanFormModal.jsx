import {useEffect} from 'react';
import {Form, InputNumber, Modal, Select} from 'antd';

const FREQUENCY_OPTIONS = [
    {value: 'DAILY', label: '日定投'},
    {value: 'WEEKLY', label: '周定投'},
    {value: 'MONTHLY', label: '月定投'},
];
const WEEK_OPTIONS = [
    {value: 1, label: '周一'}, {value: 2, label: '周二'}, {value: 3, label: '周三'},
    {value: 4, label: '周四'}, {value: 5, label: '周五'}, {value: 6, label: '周六'}, {value: 7, label: '周日'},
];

export default function DcaPlanFormModal({open, editing, onOk, onCancel, confirmLoading}) {
    const [form] = Form.useForm();
    const frequency = Form.useWatch('frequency', form);

    useEffect(() => {
        if (open) {
            form.setFieldsValue({
                enabled: editing?.enabled ?? true,
                amount: editing?.amount ?? 1000,
                frequency: editing?.frequency ?? 'WEEKLY',
                dayOfWeek: editing?.dayOfWeek ?? 1,
                dayOfMonth: editing?.dayOfMonth ?? 1,
            });
        }
    }, [open, editing, form]);

    const handleOk = async () => {
        const values = await form.validateFields();
        onOk(values);
    };

    return (
        <Modal title={editing ? '编辑定投计划' : '新建定投计划'} open={open} onCancel={onCancel}
               onOk={handleOk} confirmLoading={confirmLoading} destroyOnHidden width={520}>
            <Form form={form} layout="vertical">
                <Form.Item label="每次金额(元)" name="amount"
                           rules={[{required: true, message: '请输入金额'}]}>
                    <InputNumber min={1} precision={2} className="full-width" placeholder="如 1000"/>
                </Form.Item>
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
                <p style={{color: '#888', fontSize: 12}}>
                    激活后,系统在每个交易日 14:55 自动生成 INVEST 交易(PENDING),次日凌晨确认份额。
                    日定投每个交易日执行;周定投按所选星期;月定投按所选日期,遇节假日顺延到下一交易日。
                </p>
            </Form>
        </Modal>
    );
}
