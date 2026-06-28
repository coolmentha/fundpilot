import {useState} from 'react';
import {AutoComplete, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography} from 'antd';
import {DeleteOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons';
import {App} from 'antd';
import {Link} from 'react-router-dom';
import {useArchiveFund, useFunds, useFundSearch, useSaveFund} from '../api/hooks.js';
import {fundCategoryOptions, labels, money, text, signedMoney, signedPercent, pnlColor} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';

const {Title} = Typography;

// 新建表单初始值:仅计划总仓位有默认值,基金身份由搜索框选中后带入。
const emptyForm = {fundCode: '', fundName: '', fundCategory: null, fundSubType: null,
    benchmarkIndexCode: '', plannedTotalAmount: 100000};

export default function FundsPage() {
    const {message} = App.useApp();
    const {data: funds, isLoading, refetch} = useFunds();
    const saveFund = useSaveFund();
    const archiveFund = useArchiveFund();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form] = Form.useForm();
    const [searchQuery, setSearchQuery] = useState('');

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
            plannedTotalAmount: fund.plannedTotalAmount,
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
            await saveFund.mutateAsync({id: editing?.id, body: values});
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
        {title: '代码', dataIndex: 'fundCode', width: 110},
        {title: '名称', dataIndex: 'fundName', width: 200, ellipsis: true},
        {title: '类型', dataIndex: 'fundCategory', width: 90, render: (v) => <StatusTag value={v}/>},
        {title: '子类', dataIndex: 'fundSubType', width: 100, render: (v) => text(v)},
        {title: '状态', dataIndex: 'status', width: 100, render: (v) => <StatusTag value={v}/>},
        {title: '计划仓位', dataIndex: 'plannedTotalAmount', width: 140, align: 'right', render: money},
        {
            title: '今日涨跌/盈亏', width: 130, align: 'right',
            render: (_, r) => (
                <div className="pnl-cell">
                    <div style={{color: pnlColor(r.dailyChangePct)}}>{signedPercent(r.dailyChangePct)}</div>
                    <div style={{color: pnlColor(r.dailyPnl)}}>{signedMoney(r.dailyPnl)}</div>
                </div>
            ),
        },
        {
            title: '持仓市值', dataIndex: 'holdingAmount', width: 140, align: 'right',
            render: (v) => v === null || v === undefined ? '-' : money(v),
        },
        {
            title: '总盈亏', dataIndex: 'totalPnl', width: 130, align: 'right',
            render: (v) => <span style={{color: pnlColor(v)}}>{signedMoney(v)}</span>,
        },
        {title: '跟踪指数', dataIndex: 'benchmarkIndexCode', width: 110, render: (v) => text(v)},
        {
            title: '操作', width: 220, render: (_, row) => (
                <Space>
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
        <Space direction="vertical" size={16} className="full-width">
            <Card title={<Title level={4}>基金管理</Title>} extra={
                <Space>
                    <Button icon={<ReloadOutlined/>} onClick={() => refetch()}>刷新</Button>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={openCreate}>新建基金</Button>
                </Space>
            }>
                <Table rowKey="id" size="small" loading={isLoading} dataSource={funds} columns={columns}
                       pagination={false} scroll={{x: 1470}}/>
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
                               help="自动识别,可手动调整(决定默认档位和硬约束上限)">
                        <Select options={fundCategoryOptions} allowClear placeholder="自动识别,可调整"/>
                    </Form.Item>
                    <Form.Item label="计划总仓位" name="plannedTotalAmount"
                               rules={[{required: true, message: '请输入计划总仓位'}]}>
                        <InputNumber min={0} precision={2} className="full-width"
                                     formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                                     parser={(v) => v.replace(/,/g, '')}/>
                    </Form.Item>
                </Form>
            </Modal>
        </Space>
    );
}
