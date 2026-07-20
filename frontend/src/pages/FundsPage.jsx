import {useState} from 'react';
import {Alert, AutoComplete, Button, Card, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography} from 'antd';
import {AppstoreOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons';
import {App} from 'antd';
import dayjs from 'dayjs';
import {Link} from 'react-router-dom';
import {useArchiveFund, useDcaBudgetSummary, useFundGroups, useFunds, useFundSearch, useSaveFund} from '../api/hooks.js';
import {fundCategoryOptions, labels, money, percent, text, signedMoney, signedPercent, pnlColor} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import {estimateStatusText} from '../querySafety.js';
import DcaBudgetOverview from '../components/DcaBudgetOverview.jsx';
import {buildFundPositionWarnings} from '../positionWarnings.js';
import FundGroupTabs from '../components/FundGroupTabs.jsx';
import FundGroupManagerModal from '../components/FundGroupManagerModal.jsx';
import {ALL_GROUPS_KEY, filterFundsByGroup} from '../fundGroups.js';

const {Title} = Typography;

// 新建表单初始值:基金身份由搜索框选中后带入。已有持仓字段默认空。
const emptyForm = {fundCode: '', fundName: '', fundCategory: null, fundSubType: null,
    benchmarkIndexCode: '', positionWarningEnabled: true, positionWarningRatioPct: 30,
    initialHoldingShares: null, costPerShare: null, openedAt: null, groupNames: []};

export default function FundsPage() {
    const {message} = App.useApp();
    const {data: funds, isLoading, refetch} = useFunds();
    const {data: fundGroups} = useFundGroups();
    const {
        data: dcaBudgetSummary,
        isLoading: isDcaBudgetLoading,
        isError: isDcaBudgetError,
        refetch: refetchDcaBudget,
    } = useDcaBudgetSummary();
    const saveFund = useSaveFund();
    const archiveFund = useArchiveFund();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form] = Form.useForm();
    const [searchQuery, setSearchQuery] = useState('');
    const [activeGroup, setActiveGroup] = useState(ALL_GROUPS_KEY);
    const [groupManagerOpen, setGroupManagerOpen] = useState(false);
    const initialHoldingShares = Form.useWatch('initialHoldingShares', form);
    const positionWarningEnabled = Form.useWatch('positionWarningEnabled', form);
    const rows = buildFundPositionWarnings(funds);
    const effectiveActiveGroup = activeGroup === ALL_GROUPS_KEY
        || (fundGroups || []).some((group) => String(group.id) === activeGroup) ? activeGroup : ALL_GROUPS_KEY;
    const displayRows = filterFundsByGroup(rows, effectiveActiveGroup);

    // 字典搜索(仅新建时用;编辑时基金身份已固定)
    const {data: searchResults, isFetching: searching} = useFundSearch(searchQuery);
    const options = (searchResults || []).map((r) => ({
        value: r.fundCode,
        label: (
            <Space size="small" style={{width: '100%', justifyContent: 'space-between'}}>
                <Space size="small">
                    <span className="num-cell">{r.fundCode}</span>
                    <span>{r.fundName}</span>
                </Space>
                <Space size={4}>
                    {r.fundSubType && <Tag>{labels[r.fundSubType] || r.fundSubType}</Tag>}
                    {r.fundCategory && <Tag color="blue">{labels[r.fundCategory] || r.fundCategory}</Tag>}
                </Space>
            </Space>
        ),
        // 把整条候选挂到 option 上,选中时一次性取用
        candidate: r,
    }));

    const openCreate = () => {
        setEditing(null);
        form.setFieldsValue(emptyForm);
        setSearchQuery('');
        setOpen(true);
    };
    const openEdit = (fund) => {
        setEditing(fund);
        form.setFieldsValue({
            fundCode: fund.fundCode,
            fundName: fund.fundName,
            fundCategory: fund.fundCategory,
            fundSubType: fund.fundSubType,
            benchmarkIndexCode: fund.benchmarkIndexCode,
            positionWarningEnabled: fund.positionWarningEnabled !== false,
            positionWarningRatioPct: Number(fund.positionWarningRatio ?? 0.3) * 100,
            groupNames: (fund.groups || []).map((group) => group.name),
        });
        setOpen(true);
    };

    // 搜索框选中候选:一次性回填 code/name/类型/子类/跟踪指数
    const onSelectCandidate = (value, option) => {
        const c = option?.candidate;
        if (!c) {
            return;
        }
        form.setFieldsValue({
            fundCode: c.fundCode,
            fundName: c.fundName,
            fundCategory: c.fundCategory,
            fundSubType: c.fundSubType,
            benchmarkIndexCode: c.benchmarkIndexCode || '',
        });
        setSearchQuery('');
    };

    const submit = async () => {
        try {
            const values = await form.validateFields();
            // openedAt:DatePicker 返回 dayjs,提交前转 ISO 字符串(后端 Instant 解析);未选则不传(后端用 now)
            const {positionWarningRatioPct, ...requestValues} = values;
            const normalized = {...requestValues, positionWarningRatio: positionWarningRatioPct / 100};
            const body = values.openedAt
                ? {...normalized, openedAt: values.openedAt.startOf('day').toISOString()}
                : {...normalized, openedAt: null};
            await saveFund.mutateAsync({id: editing?.id, body});
            message.success(editing ? '基金已更新' : '基金已新建');
            setOpen(false);
        } catch (e) {
            // 表单校验失败:Antd 已在字段下提示,不再弹 message;后端业务异常由全局 mutation onError 弹
            if (e?.errorFields) return;
            throw e;
        }
    };

    const archive = async (fund) => {
        await archiveFund.mutateAsync(fund.id);
        message.success(`已归档 ${fund.fundName}`);
    };

    const columns = [
        {title: '代码', dataIndex: 'fundCode', width: 96},
        {title: '名称', dataIndex: 'fundName', width: 180, ellipsis: true,
            render: (v, r) => <Link to={`/funds/${r.id}`}>{v}</Link>},
        {title: '类型', dataIndex: 'fundCategory', width: 88, responsive: ['md'], render: (v) => <StatusTag value={v}/>},
        {title: '子类', dataIndex: 'fundSubType', width: 96, responsive: ['lg'], render: (v) => text(v)},
        {title: '状态', dataIndex: 'status', width: 96, render: (v) => <StatusTag value={v}/>},
        {title: '分组', dataIndex: 'groups', width: 160, responsive: ['md'],
            render: (groups) => groups?.length ? <Space size={[4, 4]} wrap>
                {groups.map((group) => <Tag key={group.id}>{group.name}</Tag>)}
            </Space> : '-'},
        {
            title: '仓位提醒', width: 132, align: 'right',
            render: (_, record) => {
                if (record.positionRatio === null) return '-';
                const warningText = record.positionWarningEnabled
                    ? (record.positionWarningExceeded
                        ? `已超 ${percent(record.positionWarningRatio)} 提醒线`
                        : `提醒线 ${percent(record.positionWarningRatio)}`)
                    : '提醒已关闭';
                return (
                    <div className={`position-warning-cell${record.positionWarningExceeded ? ' is-exceeded' : ''}`}>
                        <strong>{percent(record.positionRatio)}</strong>
                        <span>{warningText}</span>
                    </div>
                );
            },
        },
        {
            title: '今日涨跌/盈亏', width: 126, align: 'right',
            render: (_, r) => estimateStatusText(r.estimateStatus) ? (
                <span className={r.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{estimateStatusText(r.estimateStatus)}</span>
            ) : (
                <div className="pnl-cell">
                    <div style={{color: pnlColor(r.dailyChangePct)}}>
                        {signedPercent(r.dailyChangePct)}
                        {r.isEstimated && <span className="estimate-tag">估</span>}
                    </div>
                    <div style={{color: pnlColor(r.dailyPnl)}}>{signedMoney(r.dailyPnl)}</div>
                </div>
            ),
        },
        {
            title: '持仓市值', dataIndex: 'holdingAmount', width: 126, align: 'right', responsive: ['sm'],
            render: (v) => v === null || v === undefined ? '-' : money(v),
        },
        {
            title: '成本单价', dataIndex: 'costPerShare', width: 104, align: 'right', responsive: ['lg'],
            render: (v) => v === null || v === undefined ? '-' : money(v),
        },
        {
            title: '总盈亏', dataIndex: 'totalPnl', width: 120, align: 'right', responsive: ['md'],
            render: (v) => <span style={{color: pnlColor(v)}}>{signedMoney(v)}</span>,
        },
        {title: '跟踪指数', dataIndex: 'benchmarkIndexCode', width: 104, responsive: ['lg'], render: (v) => text(v)},
        {
            title: '操作', width: 168, render: (_, row) => (
                <Space size="small" wrap>
                    <Link to={`/funds/${row.id}`}>详情</Link>
                    <Link to={`/signals?fundId=${row.id}`}>信号</Link>
                    <a onClick={() => openEdit(row)}>编辑</a>
                    <Popconfirm title={`归档 ${row.fundName}?`} description="软删除基金及其全部关联数据,可联系管理员恢复。"
                                okText="归档" okButtonProps={{danger: true}} cancelText="取消"
                                onConfirm={() => archive(row)}>
                        <a className="danger-link"><DeleteOutlined/> 归档</a>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <Space direction="vertical" size={16} className="full-width funds-page">
            <Card title={<Title level={4}>基金管理</Title>} extra={
                <Space>
                    <Button icon={<AppstoreOutlined/>} onClick={() => setGroupManagerOpen(true)}>管理分组</Button>
                    <Button icon={<ReloadOutlined/>} onClick={() => { refetch(); refetchDcaBudget(); }}>刷新</Button>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>新建基金</Button>
                </Space>
            }>
                <DcaBudgetOverview summary={dcaBudgetSummary} isLoading={isDcaBudgetLoading}
                                   isError={isDcaBudgetError} onRetry={refetchDcaBudget}/>
                <FundGroupTabs groups={fundGroups} activeKey={effectiveActiveGroup} onChange={setActiveGroup}/>
                <Table rowKey="id" size="small" loading={isLoading} dataSource={displayRows} columns={columns}
                       pagination={false} scroll={{x: 'max-content'}}/>
            </Card>
            <Modal title={editing ? '编辑基金' : '新建基金'} open={open} onCancel={() => setOpen(false)}
                   onOk={submit} confirmLoading={saveFund.isPending} destroyOnHidden width={560}>
                <Form form={form} layout="vertical">
                    {editing ? (
                        // 编辑:基金身份只读展示
                        <>
                            <Form.Item label="基金代码">
                                <span className="num-cell">{form.getFieldValue('fundCode')}</span>
                            </Form.Item>
                            <Form.Item label="基金名称">
                                <span>{form.getFieldValue('fundName')}</span>
                            </Form.Item>
                        </>
                    ) : (
                        // 新建:搜索框自动补全,选中后回填全部身份字段
                        <Form.Item label="搜索基金(代码或名称)" required
                                   help="输入代码或名称,从字典候选中选中后自动回填类型/子类/跟踪指数">
                            <AutoComplete
                                value={searchQuery}
                                options={options}
                                style={{width: '100%'}}
                                loading={searching}
                                placeholder="例如 510300 或 沪深300"
                                onChange={setSearchQuery}
                                onSelect={onSelectCandidate}
                                filterOption={false}
                                allowClear
                            />
                            {/* 选中后展示已回填的身份(只读),便于用户确认 */}
                            {form.getFieldValue('fundCode') && (
                                <Space size="small" style={{marginTop: 8}} wrap>
                                    <Tag color="green">{form.getFieldValue('fundCode')}</Tag>
                                    <span>{form.getFieldValue('fundName')}</span>
                                    {form.getFieldValue('fundSubType') &&
                                        <Tag>{labels[form.getFieldValue('fundSubType')]}</Tag>}
                                    {form.getFieldValue('fundCategory') &&
                                        <Tag color="blue">{labels[form.getFieldValue('fundCategory')]}</Tag>}
                                    {form.getFieldValue('benchmarkIndexCode') &&
                                        <Tag>{form.getFieldValue('benchmarkIndexCode')}</Tag>}
                                </Space>
                            )}
                        </Form.Item>
                    )}
                    {/* 隐藏字段:搜索框选中后 setFieldsValue 写入,需注册 name 才能被 validateFields 返回 */}
                    <Form.Item name="fundCode" hidden><Input/></Form.Item>
                    <Form.Item name="fundName" hidden><Input/></Form.Item>
                    <Form.Item name="fundSubType" hidden><Input/></Form.Item>
                    <Form.Item name="benchmarkIndexCode" hidden><Input/></Form.Item>
                    <Form.Item label="基金类型" name="fundCategory"
                               help="自动识别,可手动调整">
                        <Select options={fundCategoryOptions} allowClear placeholder="自动识别,可调整"/>
                    </Form.Item>
                    <Form.Item label="分组" name="groupNames" help="可选择多个分组，输入新名称后回车即可创建">
                        <Select mode="tags" maxLength={20} tokenSeparators={[',']} placeholder="可选"
                                options={(fundGroups || []).map((group) => ({value: group.name, label: group.name}))}/>
                    </Form.Item>
                    <Form.Item label="仓位提醒" name="positionWarningEnabled" valuePropName="checked">
                        <Switch checkedChildren="开" unCheckedChildren="关"/>
                    </Form.Item>
                    <Form.Item label="提醒线" name="positionWarningRatioPct"
                               rules={[{required: true, message: '请输入仓位提醒线'},
                                   {type: 'number', min: 1, max: 100, message: '提醒线必须在 1% 到 100% 之间'}]}>
                        <InputNumber min={1} max={100} precision={2} className="full-width"
                                     disabled={!positionWarningEnabled}
                                     formatter={(value) => value === undefined || value === null ? '' : `${value}%`}
                                     parser={(value) => value?.replace('%', '')}/>
                    </Form.Item>
                    {!editing && (
                        <Form.Item label="持有份额（可选）" name="initialHoldingShares"
                                   help="现有持仓的实际份额。不填则创建空仓基金"
                                   rules={[{type: 'number', min: 0.01, message: '持有份额必须至少为 0.01'}]}>
                            <InputNumber min={0.01} precision={2} className="full-width" placeholder="已有持仓份额,不填则空仓"
                                         formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                                         parser={(v) => v.replace(/,/g, '')}/>
                        </Form.Item>
                    )}
                    {!editing && initialHoldingShares > 0 && (
                        <Alert type="info" showIcon style={{marginTop: -8}}
                               message="将按持有份额建立初始持仓"
                               description={
                                   <div>
                                        <p>{initialHoldingShares.toLocaleString()} 份会直接记入事实持仓。</p>
                                        <p>首笔流水金额按最近确认净值核算，当前市值随行情变化。</p>
                                   </div>
                               }/>
                    )}
                    {!editing && initialHoldingShares > 0 && (
                        <Form.Item label="成本单价（可选）" name="costPerShare"
                                   help="持仓成本价(每份)。不填默认用 T-1 净值作为初始成本价">
                            <InputNumber min={0.0001} precision={4} className="full-width" placeholder="不填则用净值,填 0 无效"
                                         formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                                         parser={(v) => v.replace(/,/g, '')}/>
                        </Form.Item>
                    )}
                    {!editing && initialHoldingShares > 0 && (
                        <Form.Item label="建仓时间（可选）" name="openedAt"
                                   help="用户记得的大致建仓时点,影响移动止盈的高点起算;不填则用当前时间">
                            <DatePicker className="full-width" placeholder="选填,默认当前时间"
                                        disabledDate={(d) => d && d.isAfter(dayjs().endOf('day'))}/>
                        </Form.Item>
                    )}
                </Form>
            </Modal>
            <FundGroupManagerModal open={groupManagerOpen} groups={fundGroups}
                                   onCancel={() => setGroupManagerOpen(false)}/>
        </Space>
    );
}
