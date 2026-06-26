import {useState} from 'react';
import {App, Button, Card, Popconfirm, Space, Table, Typography} from 'antd';
import {PlayCircleOutlined, PlusOutlined} from '@ant-design/icons';
import {
    useActiveStrategy,
    useBacktests,
    useCreateStrategy,
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

    const tierColumns = [
        {title: '状态', dataIndex: 'status', width: 100, render: (v) => <StatusTag value={v}/>},
        {title: '一档', width: 100, render: (_, r) => `${percent(r.tier1Drawdown)} / ${percent(r.tier1Ratio)}`},
        {title: '二档', width: 100, render: (_, r) => `${percent(r.tier2Drawdown)} / ${percent(r.tier2Ratio)}`},
        {title: '三档', width: 100, render: (_, r) => `${percent(r.tier3Drawdown)} / ${percent(r.tier3Ratio)}`},
        {title: '四档', width: 100, render: (_, r) => `${percent(r.tier4Drawdown)} / ${percent(r.tier4Ratio)}`},
        {title: '周冷静', dataIndex: 'weeklyCoolDownThreshold', width: 90, render: percent},
        {title: '止盈回落', dataIndex: 'stopLossPullbackPercent', width: 90, render: percent},
        {
            title: '操作', width: 280, render: (_, r) => (
                <Space wrap size="small">
                    {r.status === 'PENDING_CALIBRATION' &&
                        <Button size="small" onClick={() => { setEditing(r); setModalOpen(true); }}>编辑</Button>}
                    {r.status === 'PENDING_CALIBRATION' &&
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
        {title: '通过', dataIndex: 'passed', width: 80, render: (v) => <StatusTag value={v ? 'PASSED' : 'CANCELLED'}/>},
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
                        <Text>一档 {percent(active.tier1Drawdown)}</Text>
                        <Text>二档 {percent(active.tier2Drawdown)}</Text>
                        <Text>三档 {percent(active.tier3Drawdown)}</Text>
                        <Text>四档 {percent(active.tier4Drawdown)}</Text>
                    </Space>
                </Card>
            )}
            <div style={{display: 'flex', justifyContent: 'flex-end'}}>
                <Button type="primary" icon={<PlusOutlined/>}
                        onClick={() => { setEditing(null); setModalOpen(true); }}>新建策略</Button>
            </div>
            <Table rowKey="id" size="small" loading={isLoading} dataSource={strategies}
                   columns={tierColumns} pagination={false} scroll={{x: 1100}}/>
            {backtestStrategyId && (
                <Card title={`回测历史 #${backtestStrategyId}`} extra={
                    <Button size="small" onClick={() => setBacktestStrategyId(null)}>关闭</Button>}>
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
