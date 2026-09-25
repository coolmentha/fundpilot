import {useEffect, useState} from 'react';
import {
    Alert, Button, Card, Descriptions, Form, InputNumber, List, Modal, Radio, Select, Space, Steps, Tag, Typography,
} from 'antd';
import {DeleteOutlined, PlusOutlined} from '@ant-design/icons';
import {date, labels} from '../constants.js';

const SCOPE_OPTIONS = [
    {value: 'GLOBAL', label: '全部关注基金'},
    {value: 'FUND', label: '指定基金'},
];

// 规则种类：与后端 AlertRuleKind 的取值一一对应，中文名与后端 label() 保持一致。
const KIND_OPTIONS = [
    {value: 'CONDITION', label: '条件提醒'},
    {value: 'LOGIC_BROKEN', label: '逻辑破坏止损'},
    {value: 'TRAILING_STOP', label: '回撤止盈'},
];
const kindLabel = (kind) => KIND_OPTIONS.find((option) => option.value === kind)?.label || kind;

// 组合逻辑先只做「全部满足」，不做嵌套、不做 OR。
const MATCH_ALL = 'ALL';
const SCOPE_FIELDS = ['scope', 'portfolioFundId', 'kind'];
const PREVIEW_FUND_LIMIT = 5;

/**
 * 逻辑破坏止损模板：指标码、关系与参数默认值一律按指标元数据查得，前端不硬编码第二份枚举。
 * 三条条件为：跌破年线、周线 MACD 绿柱、绿柱较上周扩大。
 */
const LOGIC_BROKEN_CONDITIONS = [
    {indicator: 'PRICE_VS_MA', relation: 'BELOW', params: {window: 250}},
    {indicator: 'WEEKLY_MACD_HISTOGRAM', relation: 'BELOW'},
    {indicator: 'WEEKLY_MACD_HISTOGRAM', relation: 'DECREASING'},
];

/** 回撤止盈四档预设：照搬旧纪律策略的推荐值（D6），比例一律用小数表示。 */
const TAKE_PROFIT_PRESETS = [
    {
        key: 'BROAD_BASE', button: '宽基指数 15%/6%',
        description: '适用于宽基指数基金，启动 15%、回撤 6%、收割 50%、最低保留 50%',
        params: {activation: 0.15, pullback: 0.06, harvest: 0.5, minimumHolding: 0.5, maxSingleSell: 0.2, cooldownDays: 10},
    },
    {
        key: 'SECTOR', button: '行业指数 20%/8%',
        description: '适用于行业/主题指数基金，波动更大，启动 20%、回撤 8%、收割 50%、最低保留 40%',
        params: {activation: 0.2, pullback: 0.08, harvest: 0.5, minimumHolding: 0.4, maxSingleSell: 0.2, cooldownDays: 10},
    },
    {
        key: 'ACTIVE', button: '主动型 15%/7%',
        description: '适用于主动管理型基金，启动 15%、回撤 7%、收割 50%、最低保留 50%',
        params: {activation: 0.15, pullback: 0.07, harvest: 0.5, minimumHolding: 0.5, maxSingleSell: 0.2, cooldownDays: 10},
    },
    {
        key: 'MIXED', button: '混合型 12%/5%',
        description: '适用于股债混合型基金，波动较小，启动 12%、回撤 5%、收割 40%、最低保留 60%',
        params: {activation: 0.12, pullback: 0.05, harvest: 0.4, minimumHolding: 0.6, maxSingleSell: 0.2, cooldownDays: 10},
    },
];

const TAKE_PROFIT_NAMES = ['activation', 'pullback', 'harvest', 'minimumHolding', 'maxSingleSell', 'cooldownDays'];

/** 比例字段校验：0/1 是否可取与后端 TakeProfitParams 一致。 */
const ratioRules = (label, {zeroAllowed, oneAllowed}) => [
    {required: true, message: `请填写${label}`},
    {
        validator: (_, value) => {
            if (value === undefined || value === null || value === '') return Promise.resolve();
            const number = Number(value);
            if (!Number.isFinite(number)) return Promise.reject(new Error(`${label}必须为数字`));
            if (zeroAllowed ? number < 0 : number <= 0) {
                return Promise.reject(new Error(`${label}必须${zeroAllowed ? '不小于' : '大于'} 0`));
            }
            if (oneAllowed ? number > 1 : number >= 1) {
                return Promise.reject(new Error(`${label}必须${oneAllowed ? '不大于' : '小于'} 1`));
            }
            return Promise.resolve();
        },
    },
];

const cooldownRules = [
    {required: true, message: '请填写冷静期交易日'},
    {
        validator: (_, value) => {
            if (value === undefined || value === null || value === '') return Promise.resolve();
            const number = Number(value);
            return Number.isInteger(number) && number >= 0 && number <= 250
                ? Promise.resolve()
                : Promise.reject(new Error('冷静期交易日必须是 0 到 250 之间的整数'));
        },
    },
];

const indicatorOf = (indicators, code) => (indicators ?? []).find((item) => item.code === code);

const relationOf = (indicator, relation) =>
    (indicator?.relations ?? []).find((item) => item.relation === relation);

const defaultParams = (indicator) =>
    Object.fromEntries((indicator?.parameters ?? []).map((parameter) => [parameter.name, parameter.defaultValue]));

/** 关系的初始阈值：不比较阈值的关系为空；给出默认阈值的关系预填，否则留空必填。 */
const initialThreshold = (indicator, relation) => {
    if (!relation?.thresholded) return null;
    return relation.defaultThreshold === null || relation.defaultThreshold === undefined
        ? null : Number(relation.defaultThreshold);
};

/** 一条全新条件的默认形态：第一个关系 + 参数默认值。 */
const newCondition = (indicator) => {
    const relation = indicator?.relations?.[0];
    return {
        indicator: indicator?.code,
        relation: relation?.relation,
        params: defaultParams(indicator),
        value: initialThreshold(indicator, relation),
    };
};

export default function AlertRuleFormModal(
    {open, editing, funds, indicators, onOk, onCancel, onPreview, confirmLoading},
) {
    const [form] = Form.useForm();
    const [step, setStep] = useState(0);
    const [preview, setPreview] = useState(null);
    const [previewFailed, setPreviewFailed] = useState(false);
    const [previewing, setPreviewing] = useState(false);
    const values = Form.useWatch([], form) ?? {};
    const scope = values.scope ?? 'GLOBAL';
    const kind = values.kind ?? 'CONDITION';
    const conditions = values.conditions ?? [];
    const needsConditions = kind !== 'TRAILING_STOP';

    // 每次打开由父组件换 key 重挂载，这里只负责把已有规则回显到表单。
    useEffect(() => {
        if (!open) return;
        if (editing) {
            form.setFieldsValue({
                scope: editing.scope,
                portfolioFundId: editing.portfolioFundId ?? undefined,
                kind: editing.kind ?? 'CONDITION',
                conditions: (editing.conditions ?? []).map((condition) => {
                    const indicator = indicatorOf(indicators, condition.indicator);
                    return {
                        indicator: condition.indicator,
                        relation: condition.relation,
                        params: {...defaultParams(indicator), ...(condition.params ?? {})},
                        value: condition.value === null || condition.value === undefined
                            ? null : Number(condition.value),
                    };
                }),
                takeProfit: editing.takeProfit ? {
                    activation: Number(editing.takeProfit.activation),
                    pullback: Number(editing.takeProfit.pullback),
                    harvest: Number(editing.takeProfit.harvest),
                    minimumHolding: Number(editing.takeProfit.minimumHolding),
                    maxSingleSell: Number(editing.takeProfit.maxSingleSell),
                    cooldownDays: editing.takeProfit.cooldownDays,
                } : undefined,
            });
            return;
        }
        form.setFieldsValue({scope: 'GLOBAL', portfolioFundId: undefined, kind: 'CONDITION', conditions: []});
    }, [open, editing, indicators, form]);

    const toPayload = (raw) => {
        const rawKind = raw.kind ?? 'CONDITION';
        const payload = {
            scope: raw.scope,
            portfolioFundId: raw.scope === 'FUND' ? raw.portfolioFundId : null,
            kind: rawKind,
            match: MATCH_ALL,
            enabled: editing ? editing.enabled : true,
        };
        // 回撤止盈不带条件；其余种类带条件且不带止盈参数（与后端 AlertRuleDraft 的语义一致）。
        if (rawKind !== 'TRAILING_STOP') {
            payload.conditions = (raw.conditions ?? []).map((condition) => {
                const indicator = indicatorOf(indicators, condition.indicator);
                const relation = relationOf(indicator, condition.relation);
                const params = {};
                for (const parameter of indicator?.parameters ?? []) {
                    const value = condition.params?.[parameter.name];
                    params[parameter.name] = value === undefined || value === null ? parameter.defaultValue : value;
                }
                return {
                    indicator: condition.indicator,
                    relation: condition.relation,
                    params,
                    value: relation?.thresholded ? condition.value ?? null : null,
                };
            });
        }
        if (rawKind === 'TRAILING_STOP') {
            const takeProfit = raw.takeProfit ?? {};
            payload.takeProfit = Object.fromEntries(TAKE_PROFIT_NAMES.map((name) => [name, takeProfit[name] ?? null]));
        }
        return payload;
    };

    /** 用最近一个交易日的行情与持仓试算当前草稿，只读不落库。 */
    const runPreview = () => {
        if (!onPreview) return;
        setPreviewing(true);
        setPreviewFailed(false);
        onPreview(toPayload(form.getFieldsValue()))
            .then(setPreview)
            .catch(() => {
                setPreview(null);
                setPreviewFailed(true);
            })
            .finally(() => setPreviewing(false));
    };

    const next = () => form.validateFields(step === 0 ? SCOPE_FIELDS : undefined)
        .then(() => {
            setStep(step + 1);
            // 进入预览步骤时立即试算一次，避免用户看到空白结果。
            if (step === 1) runPreview();
        })
        .catch(() => {});

    const changeIndicator = (index, code) => {
        form.setFieldValue(['conditions', index], newCondition(indicatorOf(indicators, code)));
    };

    const changeRelation = (index, relation) => {
        const indicator = indicatorOf(indicators, conditions[index]?.indicator);
        form.setFieldValue(['conditions', index, 'value'], initialThreshold(indicator, relationOf(indicator, relation)));
    };

    /** 按指标元数据把模板条件展开为表单项；元数据缺失的指标直接跳过。 */
    const toTemplateConditions = (templateConditions) => templateConditions.flatMap((condition) => {
        const indicator = indicatorOf(indicators, condition.indicator);
        const relation = relationOf(indicator, condition.relation);
        if (!indicator || !relation) return [];
        return [{
            indicator: indicator.code,
            relation: relation.relation,
            params: {...defaultParams(indicator), ...(condition.params ?? {})},
            value: condition.value ?? initialThreshold(indicator, relation),
        }];
    });

    const applyLogicBrokenTemplate = () => form.setFieldValue(
        'conditions', toTemplateConditions(LOGIC_BROKEN_CONDITIONS),
    );

    const applyTakeProfitPreset = (preset) => {
        form.setFieldsValue({takeProfit: {...preset.params}});
        form.setFields(TAKE_PROFIT_NAMES.map((name) => ({name: ['takeProfit', name], errors: []})));
    };

    const handleFinish = () => onOk(toPayload(form.getFieldsValue()));

    const selectedFund = (funds ?? []).find((fund) => fund.portfolioFundId === values.portfolioFundId);
    const footer = (
        <Space>
            <Button onClick={onCancel}>取消</Button>
            {step > 0 && <Button onClick={() => setStep(step - 1)}>上一步</Button>}
            {step < 2
                ? <Button type="primary" onClick={next}>下一步</Button>
                : <Button type="primary" loading={confirmLoading} onClick={handleFinish}>
                    {editing ? '保存' : '创建'}
                </Button>}
        </Space>
    );

    return (
        <Modal className="alert-rule-form-modal" title={editing ? '编辑提醒规则' : '新建提醒规则'} open={open}
               onCancel={onCancel} footer={footer} destroyOnHidden width={620}>
            <Steps className="alert-rule-wizard-steps" current={step} size="small" responsive={false}
                   items={[{title: '选择范围'}, {title: kind === 'TRAILING_STOP' ? '配置止盈参数' : '配置条件'},
                       {title: '预览确认'}]}/>
            <Form form={form} layout="vertical" initialValues={{scope: 'GLOBAL', kind: 'CONDITION', match: MATCH_ALL}}>
                <div className="alert-rule-step" style={{display: step === 0 ? 'block' : 'none'}}>
                    <Form.Item label="规则种类" name="kind" rules={[{required: true, message: '请选择规则种类'}]}>
                        <Radio.Group options={KIND_OPTIONS}/>
                    </Form.Item>
                    {kind === 'LOGIC_BROKEN' && (
                        <Alert type="info" showIcon message="逻辑破坏止损"
                               description="净值跌破年线且周线 MACD 绿柱扩大时提醒全仓卖出；非主动型基金还会要求基准指数放量下跌，主动型基金（无基准指数）不适用该条件。"/>
                    )}
                    {kind === 'TRAILING_STOP' && (
                        <Alert type="info" showIcon message="回撤止盈"
                               description="收益率达到启动门槛后，从周期峰值回撤到设定比例即提醒卖出浮盈的一部分；参数可按下方预设一键填写。"/>
                    )}
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
                        <Alert type="info" showIcon message="若某只基金已配置口径相同的单基金规则，该基金将按单基金规则提醒"/>
                    )}
                </div>
                {needsConditions ? (
                    <div className="alert-rule-step" style={{display: step === 1 ? 'block' : 'none'}}>
                        <Space wrap className="alert-rule-templates">
                            {kind === 'LOGIC_BROKEN' && (
                                <Button size="small" onClick={applyLogicBrokenTemplate}>套用逻辑破坏止损模板</Button>
                            )}
                        </Space>
                        {kind === 'LOGIC_BROKEN' && (
                            <Alert type="info" showIcon className="alert-rule-logic-broken-hint"
                                   message="逻辑破坏止损的完整口径"
                                   description="非主动型基金后端还会要求基准指数「放量下跌」，该隐含条件不在这里展示；主动型基金没有基准指数，不适用放量下跌条件。"/>
                        )}
                        <Form.List name="conditions">
                            {(fields, {add, remove}) => (
                                <>
                                    {fields.length === 0 && (
                                        <Typography.Text type="secondary">
                                            {kind === 'LOGIC_BROKEN' ? '点击上方模板一键填入逻辑破坏止损的三条条件' : '还没有条件，可手动添加'}
                                        </Typography.Text>
                                    )}
                                    {fields.map((field) => {
                                        const condition = conditions[field.name] ?? {};
                                        const indicator = indicatorOf(indicators, condition.indicator);
                                        const relation = relationOf(indicator, condition.relation);
                                        return (
                                            <Card key={field.key} size="small" className="alert-rule-condition">
                                                <Form.Item label="指标" name={[field.name, 'indicator']}
                                                           rules={[{required: true, message: '请选择指标'}]}>
                                                    <Select showSearch optionFilterProp="label" placeholder="请选择指标"
                                                            onChange={(code) => changeIndicator(field.name, code)}
                                                            options={(indicators ?? []).map((item) => ({
                                                                value: item.code, label: item.label,
                                                            }))}/>
                                                </Form.Item>
                                                {indicator && (
                                                    <Typography.Text type="secondary" className="alert-rule-condition-hint">
                                                        {indicator.description}
                                                    </Typography.Text>
                                                )}
                                                <Form.Item label="关系" name={[field.name, 'relation']}
                                                           rules={[{required: true, message: '请选择关系'}]}>
                                                    <Select placeholder="请选择关系" disabled={!indicator}
                                                            onChange={(code) => changeRelation(field.name, code)}
                                                            options={(indicator?.relations ?? []).map((item) => ({
                                                                value: item.relation, label: item.label,
                                                            }))}/>
                                                </Form.Item>
                                                {(indicator?.parameters ?? []).map((parameter) => (
                                                    <Form.Item
                                                        key={parameter.name}
                                                        label={`${parameter.label}（${parameter.minimum}–${parameter.maximum}）`}
                                                        name={[field.name, 'params', parameter.name]}
                                                        rules={[
                                                            {required: true, message: `请填写${parameter.label}`},
                                                            {type: 'number', min: parameter.minimum, max: parameter.maximum,
                                                                message: `请输入 ${parameter.minimum}–${parameter.maximum}`},
                                                        ]}>
                                                        <InputNumber precision={0} className="full-width"/>
                                                    </Form.Item>
                                                ))}
                                                {relation?.thresholded && (
                                                    <Form.Item
                                                        label={`阈值（${indicator.minimum}–${indicator.maximum}）`}
                                                        name={[field.name, 'value']}
                                                        extra="阈值为小数：0.05 表示 5%"
                                                        rules={relation.defaultThreshold === null || relation.defaultThreshold === undefined
                                                            ? [
                                                                {required: true, message: '请填写阈值'},
                                                                {type: 'number', min: Number(indicator.minimum),
                                                                    max: Number(indicator.maximum),
                                                                    message: `请输入 ${indicator.minimum}–${indicator.maximum}`},
                                                            ]
                                                            : []}>
                                                        <InputNumber className="full-width"/>
                                                    </Form.Item>
                                                )}
                                                <Button type="text" danger icon={<DeleteOutlined/>} aria-label="删除条件"
                                                        onClick={() => remove(field.name)}>删除条件</Button>
                                            </Card>
                                        );
                                    })}
                                    <Button type="dashed" block icon={<PlusOutlined/>}
                                            onClick={() => add(newCondition(indicators?.[0]))}>
                                        添加条件
                                    </Button>
                                </>
                            )}
                        </Form.List>
                        <Typography.Text type="secondary">
                            多条条件之间为「且」关系：全部满足才会提醒。
                        </Typography.Text>
                    </div>
                ) : (
                    <div className="alert-rule-step" style={{display: step === 1 ? 'block' : 'none'}}>
                        <Space wrap className="alert-rule-templates">
                            {TAKE_PROFIT_PRESETS.map((preset) => (
                                <Button key={preset.key} size="small" title={preset.description}
                                        onClick={() => applyTakeProfitPreset(preset)}>
                                    套用「{preset.button}」
                                </Button>
                            ))}
                        </Space>
                        <Form.Item label="止盈启动收益率" name={['takeProfit', 'activation']} extra="0.15 表示 15%"
                                   rules={ratioRules('止盈启动收益率', {zeroAllowed: false, oneAllowed: true})}>
                            <InputNumber min={0} max={1} step={0.01} className="full-width"/>
                        </Form.Item>
                        <Form.Item label="高点回撤比例" name={['takeProfit', 'pullback']} extra="0.06 表示 6%"
                                   rules={ratioRules('高点回撤比例', {zeroAllowed: false, oneAllowed: false})}>
                            <InputNumber min={0} max={1} step={0.01} className="full-width"/>
                        </Form.Item>
                        <Form.Item label="浮盈收割比例" name={['takeProfit', 'harvest']} extra="0.5 表示收割浮盈的 50%"
                                   rules={ratioRules('浮盈收割比例', {zeroAllowed: false, oneAllowed: true})}>
                            <InputNumber min={0} max={1} step={0.01} className="full-width"/>
                        </Form.Item>
                        <Form.Item label="最低保留仓位" name={['takeProfit', 'minimumHolding']} extra="0.5 表示最低保留 50% 仓位"
                                   rules={ratioRules('最低保留仓位', {zeroAllowed: true, oneAllowed: false})}>
                            <InputNumber min={0} max={1} step={0.01} className="full-width"/>
                        </Form.Item>
                        <Form.Item label="单次最大卖出比例" name={['takeProfit', 'maxSingleSell']} extra="0.2 表示单次最多卖出 20%"
                                   rules={ratioRules('单次最大卖出比例', {zeroAllowed: false, oneAllowed: true})}>
                            <InputNumber min={0} max={1} step={0.01} className="full-width"/>
                        </Form.Item>
                        <Form.Item label="冷静期交易日" name={['takeProfit', 'cooldownDays']} extra="触发后至少间隔多少个交易日才再次提醒"
                                   rules={cooldownRules}>
                            <InputNumber min={0} max={250} precision={0} className="full-width"/>
                        </Form.Item>
                        <Typography.Text type="secondary">
                            比例一律填小数：0.15 表示 15%；冷静期填整数交易日（0–250）。
                        </Typography.Text>
                    </div>
                )}
                <div className="alert-rule-step" style={{display: step === 2 ? 'block' : 'none'}}>
                    <Descriptions className="alert-rule-summary" size="small" column={1} bordered>
                        <Descriptions.Item label="规则种类">{kindLabel(kind)}</Descriptions.Item>
                        <Descriptions.Item label="范围">
                            {labels[scope] || '-'}
                            {scope === 'FUND' && selectedFund ? ` · ${selectedFund.fundName}` : ''}
                        </Descriptions.Item>
                        <Descriptions.Item label="判定">
                            {needsConditions
                                ? `共 ${conditions.length} 条条件，全部满足才提醒`
                                : '按回撤止盈参数与周期峰值回撤判定'}
                        </Descriptions.Item>
                    </Descriptions>
                    {kind === 'TRAILING_STOP' && (
                        <Alert type="info" showIcon message="试算口径说明"
                               description="回撤止盈的试算只判断「当前收益率是否已达止盈启动门槛」；完整的高点回撤判定需要逐日累积周期峰值状态，以实际评估时的结果为准。"/>
                    )}
                    {kind === 'LOGIC_BROKEN' && (
                        <Alert type="info" showIcon message="试算口径说明"
                               description="试算不含后端自动追加的「基准指数放量下跌」条件，实际评估时非主动型基金还会要求该条件成立。"/>
                    )}
                    {previewing && <Typography.Text type="secondary">正在用最近一个交易日的数据试算…</Typography.Text>}
                    {previewFailed && <Alert type="error" showIcon message="试算失败" description="请检查条件配置后重试"/>}
                    {preview && (
                        <>
                            <Alert type={preview.hit ? 'success' : 'warning'} showIcon
                                   message={preview.hit ? '当前满足条件' : '当前不满足条件'}
                                   description={`以 ${date(preview.tradingDate)} 的数据试算`}/>
                            <List size="small" dataSource={preview.funds.slice(0, PREVIEW_FUND_LIMIT)}
                                  locale={{emptyText: '该范围下暂无可试算的基金'}}
                                  renderItem={(fund) => (
                                      <List.Item>
                                          <div className="alert-rule-preview-fund">
                                              <span>{fund.fundName} {fund.fundCode}</span>
                                              {fund.conditions.map((condition) => (
                                                  <div key={condition.text} className="alert-rule-preview-condition">
                                                      <Tag color={condition.satisfied ? 'green' : 'default'}>
                                                          {condition.satisfied ? '满足' : '不满足'}
                                                      </Tag>
                                                      <span>{condition.text}</span>
                                                      <Typography.Text type="secondary">
                                                          现值 {condition.currentValue ?? '无数据'}
                                                      </Typography.Text>
                                                  </div>
                                              ))}
                                          </div>
                                      </List.Item>
                                  )}/>
                            {preview.funds.length > PREVIEW_FUND_LIMIT && (
                                <Typography.Text type="secondary">
                                    仅显示前 {PREVIEW_FUND_LIMIT} 只，共 {preview.funds.length} 只基金
                                </Typography.Text>
                            )}
                        </>
                    )}
                    <div>
                        <Button onClick={runPreview} loading={previewing}>重新试算</Button>
                    </div>
                    <Typography.Text type="secondary">每个交易日 14:30 评估一次，同一规则当天最多提醒一次</Typography.Text>
                </div>
            </Form>
        </Modal>
    );
}