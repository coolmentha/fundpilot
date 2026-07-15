import {useState} from 'react';
import {App, Button, Card, Popconfirm, Space, Table, Tag, Typography} from 'antd';
import {PlusOutlined} from '@ant-design/icons';
import {
    useActiveDcaPlan,
    useCreateDcaPlan,
    useDcaPlanAction,
    useDcaPlans,
    useUpdateDcaPlan,
} from '../api/hooks.js';
import {money, text, datetime} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import DcaPlanFormModal from './DcaPlanFormModal.jsx';
import {dcaScheduleText} from '../dcaPlan.js';

const {Text} = Typography;

/**
 * 基金详情 · 定投计划 tab。用户配置一次,系统在定投日 14:55 自动生成 INVEST 交易。
 * 与移动止盈信号解耦:定投负责持续买入,止盈由基金绑定的策略信号独立触发。
 */
export default function FundDcaTab({fundId}) {
    const {message} = App.useApp();
    const {data: plans, isLoading} = useDcaPlans(fundId);
    const {data: active} = useActiveDcaPlan(fundId);
    const createPlan = useCreateDcaPlan(fundId);
    const updatePlan = useUpdateDcaPlan(fundId);
    const planAction = useDcaPlanAction(fundId);

    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);

    const onOk = async (values) => {
        if (editing) {
            await updatePlan.mutateAsync({id: editing.id, body: values});
            message.success('定投计划已更新');
        } else {
            await createPlan.mutateAsync(values);
            message.success('定投计划已新建并激活');
        }
        setModalOpen(false);
    };
    const doAction = async (planId, action) => {
        await planAction.mutateAsync({id: planId, action});
        message.success(`操作完成：${action}`);
    };

    const columns = [
        {title: '状态', dataIndex: 'status', width: 120, render: (v) => <StatusTag value={v}/>},
        {title: '频率', dataIndex: 'frequency', width: 100, render: (v) => text(v)},
        {title: '定投日', key: 'schedule', width: 120, render: (_, r) => dcaScheduleText(r)},
        {title: '金额', dataIndex: 'amount', width: 140, render: (v) => money(v)},
        {
            title: '启用', dataIndex: 'enabled', width: 80,
            render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? '开' : '关'}</Tag>,
        },
        {title: '创建时间', dataIndex: 'createdDate', width: 160, render: datetime},
        {
            title: '操作', width: 240, render: (_, r) => (
                <Space wrap size="small">
                    <Button size="small" onClick={() => { setEditing(r); setModalOpen(true); }}>编辑</Button>
                    {r.status !== 'EFFECTIVE' &&
                        <Popconfirm title="激活此定投计划？" onConfirm={() => doAction(r.id, 'activate')}>
                            <Button size="small" type="primary">激活</Button>
                        </Popconfirm>}
                    {r.status === 'EFFECTIVE' &&
                        <Popconfirm title={r.enabled ? '暂停自动定投？' : '恢复自动定投？'}
                                    onConfirm={() => doAction(r.id, r.enabled ? 'pause' : 'resume')}>
                            <Button size="small" type={r.enabled ? 'default' : 'primary'}>
                                {r.enabled ? '暂停' : '恢复'}
                            </Button>
                        </Popconfirm>}
                    {r.status === 'EFFECTIVE' &&
                        <Popconfirm title="停用此定投计划？" onConfirm={() => doAction(r.id, 'retire')}>
                            <Button size="small">停用</Button>
                        </Popconfirm>}
                </Space>
            ),
        },
    ];

    return (
        <Space direction="vertical" size={16} className="full-width">
            {active && (
                <Card className="data-card" size="small" title="当前生效定投计划">
                    <Space wrap>
                        <StatusTag value={active.status}/>
                        <Text>{text(active.frequency)} · {dcaScheduleText(active)} · {money(active.amount)}</Text>
                    </Space>
                </Card>
            )}
            <div style={{display: 'flex', justifyContent: 'flex-end'}}>
                <Button type="primary" icon={<PlusOutlined/>}
                        onClick={() => { setEditing(null); setModalOpen(true); }}>新建定投计划</Button>
            </div>
            <Table rowKey="id" size="small" loading={isLoading} dataSource={plans}
                   columns={columns} pagination={false}/>
            <DcaPlanFormModal open={modalOpen} editing={editing} onOk={onOk}
                              onCancel={() => setModalOpen(false)}
                              confirmLoading={createPlan.isPending || updatePlan.isPending}/>
        </Space>
    );
}
