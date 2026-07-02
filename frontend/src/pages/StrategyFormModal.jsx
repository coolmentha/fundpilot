import {useEffect} from 'react';
import {Form, InputNumber, Modal} from 'antd';

// 移动止盈参数(ADR-0015):对应后端 StrategyConfigRequest。
// 回撤分级表用 3 档(宽基默认;行业第4档留空)。每档 minYield/ratio 均为正小数(如 0.50 表 50%)。
const FIELDS = [
    {key: 'activationThreshold', label: '启动门槛', placeholder: 0.50, hint: '收益率达此值才启动止盈(宽基0.50/行业0.40)'},
    {key: 'sellRatio', label: '每次卖出比例', placeholder: 0.20, hint: '每次止盈卖当前持仓的比例(宽基0.20/行业0.25)'},
    {key: 'floorRatio', label: '底仓保留比例', placeholder: 0.40, hint: '累计最多卖 1−底仓(宽基0.40/行业0.25)'},
    {key: 'cooldownDays', label: '卖出冷却(交易日)', placeholder: 20, hint: '卖出后该天数内不再判定止盈', int: true},
    {key: 'pullbackTier1Yield', label: '一档起算收益率', placeholder: 0.50, hint: '对齐启动门槛'},
    {key: 'pullbackTier1Ratio', label: '一档回撤比例', placeholder: 0.15},
    {key: 'pullbackTier2Yield', label: '二档起算收益率', placeholder: 0.80},
    {key: 'pullbackTier2Ratio', label: '二档回撤比例', placeholder: 0.18},
    {key: 'pullbackTier3Yield', label: '三档起算收益率', placeholder: 1.50},
    {key: 'pullbackTier3Ratio', label: '三档回撤比例', placeholder: 0.20},
    {key: 'pullbackTier4Yield', label: '四档起算收益率', placeholder: null, hint: '行业用(1.30);宽基留空'},
    {key: 'pullbackTier4Ratio', label: '四档回撤比例', placeholder: null, hint: '行业用(0.17);宽基留空'},
];

export default function StrategyFormModal({open, editing, onOk, onCancel, confirmLoading}) {
    const [form] = Form.useForm();
    useEffect(() => {
        if (open) {
            const values = {};
            FIELDS.forEach((f) => {
                const v = editing?.[f.key];
                values[f.key] = v ?? f.placeholder;
            });
            // 回撤分级有效档数:有 tier4 则 4 档,否则 3 档
            values.pullbackTierCount = editing?.pullbackTierCount
                    ?? (editing?.pullbackTier4Yield != null ? 4 : 3);
            form.setFieldsValue(values);
        }
    }, [open, editing, form]);

    const handleOk = async () => {
        const values = await form.validateFields();
        // 计算 pullbackTierCount:四档 yield 填了才算 4 档
        values.pullbackTierCount = values.pullbackTier4Yield != null ? 4 : 3;
        onOk(values);
    };

    return (
        <Modal title={editing ? '编辑策略参数' : '新建策略参数'} open={open} onCancel={onCancel}
               onOk={handleOk} confirmLoading={confirmLoading} destroyOnHidden width={620}>
            <Form form={form} layout="vertical">
                <p style={{color: '#888', fontSize: 12}}>
                    移动止盈参数(ADR-0015):比例填正小数(0.20 表 20%)。回撤分级表 3 档(宽基)或 4 档(行业,填四档自动识别)。
                </p>
                <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 16px'}}>
                    {FIELDS.map((f) => (
                        <Form.Item key={f.key} label={f.label} name={f.key}
                                   extra={f.hint}
                                   rules={f.placeholder === null ? [] : [{required: true, message: '请填写'}]}>
                            <InputNumber step={f.int ? 1 : 0.01} precision={f.int ? 0 : 4}
                                         className="full-width"
                                         placeholder={f.placeholder == null ? '可选' : String(f.placeholder)}/>
                        </Form.Item>
                    ))}
                </div>
            </Form>
        </Modal>
    );
}
