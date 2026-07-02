import {useState} from 'react';
import {Card, DatePicker, Space, Table, Typography} from 'antd';
import {useSignalsRange, useSignalsToday} from '../api/hooks.js';
import {datetime, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';

const {Title, Text} = Typography;
const {RangePicker} = DatePicker;

const signalColumns = [
    {title: '类型', dataIndex: 'signalType', width: 90, render: (v) => <StatusTag value={v}/>},
    {title: '原因', dataIndex: 'reason', render: text},
    {title: '建议量', width: 120, render: (_, r) => {
        const m = r.suggestedMeasure;
        return m ? <span className="num-cell">{Number(m.value).toFixed(2)} ({text(m.measureUnit)})</span> : '-';
    }},
    {title: '警告', dataIndex: 'warnings', render: (v) => v ? <Text type="warning">{v}</Text> : '-'},
    {title: '信号时间', dataIndex: 'signalDate', width: 170, render: datetime},
];

/**
 * 基金详情 · 信号 tab。锁定单只基金，去掉基金选择器（由父路由提供 fundId）。
 */
export default function SignalTab({fundId}) {
    const {data: todaySignal, isLoading: todayLoading} = useSignalsToday(fundId);
    const [range, setRange] = useState(null);
    const from = range?.[0]?.format('YYYY-MM-DD');
    const to = range?.[1]?.format('YYYY-MM-DD');
    const {data: rangeSignals, isLoading: rangeLoading} = useSignalsRange(fundId, from, to);

    return (
        <Space direction="vertical" size={16} className="full-width">
            <Card title={<Title level={5}>今日信号</Title>}>
                <Table rowKey="id" size="small" loading={todayLoading}
                       dataSource={todaySignal ? [todaySignal] : []}
                       columns={signalColumns} pagination={false} scroll={{x: 760}}
                       locale={{emptyText: <EmptyState description="今日无信号"/>}}/>
            </Card>
            <Card title={<Title level={5}>历史信号</Title>}>
                <Space style={{marginBottom: 16}}>
                    <RangePicker value={range} onChange={setRange}/>
                </Space>
                <Table rowKey="id" size="small" loading={rangeLoading} dataSource={rangeSignals}
                       columns={signalColumns} pagination={false} scroll={{x: 760}}
                       locale={{emptyText: <EmptyState description="所选区间无信号"/>}}/>
            </Card>
        </Space>
    );
}
