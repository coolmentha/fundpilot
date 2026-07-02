import {useState} from 'react';
import {Alert, App, Button, Card, Popconfirm, Space, Table, Typography} from 'antd';
import {PlayCircleOutlined, PlusOutlined, ThunderboltOutlined} from '@ant-design/icons';
import {
    useActiveStrategy,
    useBacktests,
    useCreateStrategy,
    useOptimizeStrategy,
    useStrategies,
    useStrategyAction,
    useUpdateStrategy,
} from '../api/hooks.js';
import {percent, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import StrategyFormModal from './StrategyFormModal.jsx';

const {Text} = Typography;

/**
 * 基金详情 · 策略 tab。逻辑迁移自 StrategiesPage，去掉外壳 Card 与返回链接（由父 Tabs 承载）。
 */
export default function StrategyTab({fundId}) {
    const {message} = App.useApp();
    const {data: strategies, isLoading} = useStrategies(fundId);
    const {data: active} = useActiveStrategy(fundId);
    const createStrategy = useCreateStrategy(fundId);
    const updateStrategy = useUpdateStrategy(fundId);
    const strategyAction = useStrategyAction(fundId);
    const optimizeStrategy = useOptimizeStrategy(fundId);

    const [modalOpen, setModalOpen] = useState(false);
    const [editing, setEditing] = useState(null);
    const [backtestStrategyId, setBacktestStrategyId] = useState(null);
    const {data: backtests} = useBacktests(backtestStrategyId);

    const onOk = async (values) => {
        if (editing) {
            await updateStrategy.mutateAsync({id: editing.id, body: values});
            message.success('策略参数已更新');
        } else {
            await createStrategy.mutateAsync(values);
            message.success('策略已新建（待校准）');
        }
        setModalOpen(false);
    };
    const doAction = async (strategyId, action) => {
        await strategyAction.mutateAsync({id: strategyId, action});
        message.success(`操作完成：${action}`);
    };
    const onOptimize = async () => {
        const {id} = await optimizeStrategy.mutateAsync();
        // 寻优完成(不承诺"已通过"——落库走全窗口 calibrate,可能 CALIBRATION_FAILED,见 ADR-0007)
        message.success('寻优完成');
        // 自动展开新策略回测历史,让用户立即看到全窗口回测结果
        setBacktestStrategyId(id);
    };

    const tierColumns = [
        {title: '状态', dataIndex: 'status', width: 100, render: (v) => <StatusTag value={v}/>},
        {title: '启动门槛', dataIndex: 'activationThreshold', width: 90, render: percent},
        {title: '每次卖出', dataIndex: 'sellRatio', width: 90, render: percent},
        {title: '底仓保留', dataIndex: 'floorRatio', width: 90, render: percent},
        {title: '冷却(日)', dataIndex: 'cooldownDays', width: 90},
        {title: '回撤分级', width: 200, render: (_, r) => {
            // 拼接有效档 yield→ratio
            const tiers = [
                [r.pullbackTier1Yield, r.pullbackTier1Ratio],
                [r.pullbackTier2Yield, r.pullbackTier2Ratio],
                [r.pullbackTier3Yield, r.pullbackTier3Ratio],
                [r.pullbackTier4Yield, r.pullbackTier4Ratio],
            ].filter(([y]) => y != null);
            return tiers.map(([y, ratio], i) => (
                <span key={i} className="num-cell">
                    {i > 0 && ' / '}{percent(y)}→{percent(ratio)}
                </span>
            ));
        }},
        {
            title: '操作', width: 280, render: (_, r) => (
                <Space wrap size="small">
                    {(r.status === 'PENDING_CALIBRATION' || r.status === 'CALIBRATION_FAILED') &&
                        <Button size="small" onClick={() => { setEditing(r); setModalOpen(true); }}>编辑</Button>}
                    {(r.status === 'PENDING_CALIBRATION' || r.status === 'CALIBRATION_FAILED') &&
                        <Popconfirm title="校准并自动回测？" onConfirm={() => doAction(r.id, 'calibrate')}>
                            <Button size="small" type="primary">校准</Button>
                        </Popconfirm>}
                    {r.status === 'CALIBRATED' &&
                        <Popconfirm title="激活此策略？" onConfirm={() => doAction(r.id, 'activate')}>
                            <Button size="small" type="primary">激活</Button>
                        </Popconfirm>}
                    {r.status === 'EFFECTIVE' &&
                        <Popconfirm title="停用此策略？" onConfirm={() => doAction(r.id, 'retire')}>
                            <Button size="small">停用</Button>
                        </Popconfirm>}
                    <Button size="small" icon={<PlayCircleOutlined/>}
                            onClick={() => setBacktestStrategyId(r.id)}>回测</Button>
                </Space>
            ),
        },
    ];

    const backtestColumns = [
        {title: '通过', dataIndex: 'passed', width: 80, render: (v) => <StatusTag value={v ? 'PASSED' : 'FAILED'}/>},
        {title: '策略收益', dataIndex: 'strategyReturn', width: 110, align: 'right', render: percent},
        {title: '策略回撤', dataIndex: 'strategyMaxDrawdown', width: 110, align: 'right', render: percent},
        {title: '沪深300收益', dataIndex: 'benchmarkHs300Return', width: 120, align: 'right', render: percent},
        {title: '全仓收益', dataIndex: 'benchmarkAllInReturn', width: 110, align: 'right', render: percent},
        {title: '定投收益', dataIndex: 'benchmarkDcaReturn', width: 110, align: 'right', render: percent},
    ];

    return (
        <Space direction="vertical" size={16} className="full-width">
            {active && (
                <Card className="data-card" size="small" title="当前生效策略">
                    <Space wrap>
                        <StatusTag value={active.status}/>
                        <Text>启动门槛 {percent(active.activationThreshold)}</Text>
                        <Text>每次卖出 {percent(active.sellRatio)}</Text>
                        <Text>底仓 {percent(active.floorRatio)}</Text>
                        <Text>冷却 {active.cooldownDays} 日</Text>
                    </Space>
                </Card>
            )}
            <div style={{display: 'flex', justifyContent: 'flex-end', gap: 8}}>
                <Popconfirm title="自动网格搜索最优参数？可能耗时数秒"
                            onConfirm={onOptimize}>
                    <Button icon={<ThunderboltOutlined/>} loading={optimizeStrategy.isPending}>自动寻优</Button>
                </Popconfirm>
                <Button type="primary" icon={<PlusOutlined/>}
                        onClick={() => { setEditing(null); setModalOpen(true); }}>新建策略</Button>
            </div>
            <Table rowKey="id" size="small" loading={isLoading} dataSource={strategies}
                   columns={tierColumns} pagination={false} scroll={{x: 1100}}/>
            {backtestStrategyId && (
                <Card title={`回测历史 #${backtestStrategyId}`} extra={
                    <Button size="small" onClick={() => setBacktestStrategyId(null)}>关闭</Button>}>
                    <Alert type="info" showIcon style={{marginBottom: 12}}
                           message="历史表现不代表未来收益,样本外验证降低但无法消除过拟合风险"/>
                    <Table rowKey="id" size="small" dataSource={backtests} columns={backtestColumns}
                           pagination={false} scroll={{x: 800}}/>
                </Card>
            )}
            <StrategyFormModal open={modalOpen} editing={editing} onOk={onOk}
                               onCancel={() => setModalOpen(false)}
                               confirmLoading={createStrategy.isPending || updateStrategy.isPending}/>
        </Space>
    );
}
