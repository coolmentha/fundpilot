import {useState} from 'react';
import {Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Typography} from 'antd';
import {DeleteOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons';
import {App} from 'antd';
import {Link} from 'react-router-dom';
import {useArchiveFund, useFunds, useSaveFund} from '../api/hooks.js';
import {fundCategoryOptions, money, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';

const {Title} = Typography;

const emptyForm = {fundCode: '', fundName: '', fundCategory: 'BROAD_BASE', plannedTotalAmount: 100000};

export default function FundsPage() {
    const {message} = App.useApp();
    const {data: funds, isLoading, refetch} = useFunds();
    const saveFund = useSaveFund();
    const archiveFund = useArchiveFund();
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form] = Form.useForm();

    const openCreate = () => {
        setEditing(null);
        form.setFieldsValue(emptyForm);
        setOpen(true);
    };
    const openEdit = (fund) => {
        setEditing(fund);
        form.setFieldsValue({
            fundCode: fund.fundCode,
            fundName: fund.fundName,
            fundCategory: fund.fundCategory,
            plannedTotalAmount: fund.plannedTotalAmount,
        });
        setOpen(true);
    };
    const submit = async () => {
        const values = await form.validateFields();
        await saveFund.mutateAsync({id: editing?.id, body: values});
        message.success(editing ? '基金已更新' : '基金已新建');
        setOpen(false);
    };

    const archive = async (fund) => {
        await archiveFund.mutateAsync(fund.id);
        message.success(`已归档 ${fund.fundName}`);
    };

    const columns = [
        {title: '代码', dataIndex: 'fundCode', width: 110},
        {title: '名称', dataIndex: 'fundName', ellipsis: true},
        {title: '类型', dataIndex: 'fundCategory', width: 90, render: (v) => <StatusTag value={v}/>},
        {title: '子类', dataIndex: 'fundSubType', width: 100, render: (v) => text(v)},
        {title: '状态', dataIndex: 'status', width: 100, render: (v) => <StatusTag value={v}/>},
        {title: '计划仓位', dataIndex: 'plannedTotalAmount', width: 140, align: 'right', render: money},
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
                       pagination={false} scroll={{x: 1000}}/>
            </Card>
            <Modal title={editing ? '编辑基金' : '新建基金'} open={open} onCancel={() => setOpen(false)}
                   onOk={submit} confirmLoading={saveFund.isPending} destroyOnHidden>
                <Form form={form} layout="vertical">
                    <Form.Item label="基金代码" name="fundCode" rules={[{required: true, message: '请输入基金代码'}]}>
                        <Input placeholder="例如 510300" disabled={!!editing}/>
                    </Form.Item>
                    <Form.Item label="基金名称" name="fundName" rules={[{required: true, message: '请输入基金名称'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="基金类型" name="fundCategory">
                        <Select options={fundCategoryOptions}/>
                    </Form.Item>
                    <Form.Item label="计划总仓位" name="plannedTotalAmount">
                        <InputNumber min={0} precision={2} className="full-width"/>
                    </Form.Item>
                </Form>
            </Modal>
        </Space>
    );
}
