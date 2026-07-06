import {useState} from 'react';
import {App, Button, Card, Popconfirm, Space, Table, Typography} from 'antd';
import {PlusOutlined} from '@ant-design/icons';
import {
    useActiveStrategy,
    useCreateStrategy,
    useStrategies,
    useStrategyAction,
    useUpdateStrategy,
} from '../api/hooks.js';
import {percent} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import StrategyFormModal from './StrategyFormModal.jsx';

const {Text} = Typography;

/**
 * 基金详情 · 策略 tab。金字塔退场后只管理移动止盈参数(stopLossPullbackPercent)。
 * 回测/寻优/校准已移除——移动止盈阈值无需回测验证,直接新建→激活即可生效。
 */
export default function StrategyTab({fundId}) {
    const {message} = App.useApp();
    const {data: strategies, isLoading} = useStrategies(fundId);
    const {data: active} = useActiveStrategy(fundId);
    const createStrategy = useCreateStrategy(fundId);
    const updateStrategy = useUpdateStrategy(fundId);
    const strategyAction = useStrategyAction(fundId);

    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);

    const onOk = async (values) => {
        if (editing) {
            await updateStrategy.mutateAsync({id: editing.id, body: values});
            message.success('策略参数已更新');
        } else {
            await createStrategy.mutateAsync(values);
            message.success('策略已新建（待激活）');
        }
        setModalOpen(false);
    };
    const doAction = async (strategyId, action) => {
        await strategyAction.mutateAsync({id: strategyId, action});
        message.success(`操作完成：${action}`);
    };

    const columns = [
        {title: '状态', dataIndex: 'status', width: 140, render: (v) => <StatusTag value={v}/>},
        {title: '止盈回落', dataIndex: 'stopLossPullbackPercent', width: 140, render: percent},
        {
            title: '操作', width: 240, render: (_, r) => (
                <Space wrap size="small">
                    {r.status !== 'EFFECTIVE' &&
                        <Button size="small" onClick={() => { setEditing(r); setModalOpen(true); }}>编辑</Button>}
                    {r.status !== 'EFFECTIVE' &&
                        <Popconfirm title="激活此策略？" onConfirm={() => doAction(r.id, 'activate')}>
                            <Button size="small" type="primary">激活</Button>
                        </Popconfirm>}
                    {r.status === 'EFFECTIVE' &&
                        <Popconfirm title="停用此策略？" onConfirm={() => doAction(r.id, 'retire')}>
                            <Button size="small">停用</Button>
                        </Popconfirm>}
                </Space>
            ),
        },
    ];

    return (
        <Space direction="vertical" size={16} className="full-width">
            {active && (
                <Card className="data-card" size="small" title="当前生效策略">
                    <Space wrap>
                        <StatusTag value={active.status}/>
                        <Text>止盈回落 {percent(active.stopLossPullbackPercent)}</Text>
                    </Space>
                </Card>
            )}
            <div style={{display: 'flex', justifyContent: 'flex-end'}}>
                <Button type="primary" icon={<PlusOutlined/>}
                        onClick={() => { setEditing(null); setModalOpen(true); }}>新建策略</Button>
            </div>
            <Table rowKey="id" size="small" loading={isLoading} dataSource={strategies}
                   columns={columns} pagination={false}/>
            <StrategyFormModal open={modalOpen} editing={editing} onOk={onOk}
                               onCancel={() => setModalOpen(false)}
                               confirmLoading={createStrategy.isPending || updateStrategy.isPending}/>
        </Space>
    );
}
