import {useState} from 'react';
import {App, Button, Card, Descriptions, Popconfirm, Space, Table, Tag} from 'antd';
import {PlusOutlined} from '@ant-design/icons';
import {
    useActiveStrategy,
    useCreateStrategy,
    useStrategies,
    useStrategyAction,
    useStrategyRecommendation,
    useUpdateStrategy,
} from '../api/hooks.js';
import {percent} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import StrategyFormModal from './StrategyFormModal.jsx';

export default function StrategyTab({portfolioFundId}) {
    const {message} = App.useApp();
    const {data: strategies, isLoading} = useStrategies(portfolioFundId);
    const {data: active} = useActiveStrategy(portfolioFundId);
    const {data: recommendation, isLoading: recommendationLoading} = useStrategyRecommendation(portfolioFundId);
    const createStrategy = useCreateStrategy(portfolioFundId);
    const updateStrategy = useUpdateStrategy(portfolioFundId);
    const strategyAction = useStrategyAction(portfolioFundId);

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
        message.success(action === 'activate' ? '策略已激活' : '策略已停用');
    };

    const columns = [
        {title: '状态', dataIndex: 'status', width: 100, render: (v) => <StatusTag value={v}/>},
        {title: '启动收益', dataIndex: 'profitActivationPercent', width: 110, render: percent},
        {title: '高点回撤', dataIndex: 'stopLossPullbackPercent', width: 110, render: percent},
        {title: '浮盈收割', dataIndex: 'profitHarvestPercent', width: 110, render: percent},
        {title: '参数来源', dataIndex: 'customized', width: 110,
            render: (v) => <Tag color={v ? 'gold' : 'green'}>{v ? '自定义' : '类型推荐'}</Tag>},
        {
            title: '操作', width: 180, render: (_, strategy) => (
                <Space wrap size="small">
                    {strategy.status !== 'EFFECTIVE' &&
                        <Button size="small" onClick={() => { setEditing(strategy); setModalOpen(true); }}>编辑</Button>}
                    {strategy.status !== 'EFFECTIVE' &&
                        <Popconfirm
                            title={`激活：收益 ${percent(strategy.profitActivationPercent)}，回撤 ${percent(strategy.stopLossPullbackPercent)}`}
                            onConfirm={() => doAction(strategy.id, 'activate')}>
                            <Button size="small" type="primary">激活</Button>
                        </Popconfirm>}
                    {strategy.status === 'EFFECTIVE' &&
                        <Popconfirm title="停用此策略？" onConfirm={() => doAction(strategy.id, 'retire')}>
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
                    <Descriptions size="small" column={{xs: 1, sm: 2, md: 3}}>
                        <Descriptions.Item label="状态"><StatusTag value={active.status}/></Descriptions.Item>
                        <Descriptions.Item label="周期"><StatusTag value={active.takeProfitPhase}/></Descriptions.Item>
                        <Descriptions.Item label="来源">
                            <Tag color={active.customized ? 'gold' : 'green'}>
                                {active.customized ? '自定义' : '类型推荐'}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="启动收益">{percent(active.profitActivationPercent)}</Descriptions.Item>
                        <Descriptions.Item label="高点回撤">{percent(active.stopLossPullbackPercent)}</Descriptions.Item>
                        <Descriptions.Item label="浮盈收割">{percent(active.profitHarvestPercent)}</Descriptions.Item>
                        <Descriptions.Item label="最低保留">{percent(active.minimumHoldingPercent)}</Descriptions.Item>
                        <Descriptions.Item label="单次上限">{percent(active.maxSingleSellPercent)}</Descriptions.Item>
                        <Descriptions.Item label="冷静期">{active.cooldownTradingDays} 个交易日</Descriptions.Item>
                    </Descriptions>
                </Card>
            )}
            <div style={{display: 'flex', justifyContent: 'flex-end'}}>
                <Button type="primary" icon={<PlusOutlined/>}
                        loading={recommendationLoading} disabled={!recommendation}
                        onClick={() => { setEditing(null); setModalOpen(true); }}>新建策略</Button>
            </div>
            <Table rowKey="id" size="small" loading={isLoading} dataSource={strategies}
                   columns={columns} pagination={false} scroll={{x: 760}}/>
            <StrategyFormModal open={modalOpen} editing={editing} recommendation={recommendation} onOk={onOk}
                               onCancel={() => setModalOpen(false)}
                               confirmLoading={createStrategy.isPending || updateStrategy.isPending}/>
        </Space>
    );
}
