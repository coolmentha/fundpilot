import {useState} from 'react';
import {Button, Card, DatePicker, Empty, Select, Space, Table, Typography} from 'antd';
import {Link, useSearchParams} from 'react-router-dom';
import {ReloadOutlined} from '@ant-design/icons';
import {useFunds, useSignalsRange, useSignalsToday} from '../api/hooks.js';
import {datetime, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';
import dayjs from 'dayjs';

const {Title, Text} = Typography;
const {RangePicker} = DatePicker;

const signalColumns = (extraCol) => [
    {title: '类型', dataIndex: 'signalType', width: 90, render: (v) => <StatusTag value={v}/>},
    {title: '原因', dataIndex: 'reason', render: text},
    {title: '建议量', width: 120, render: (_, r) => {
        const m = r.suggestedMeasure;
        return m ? <span className="num-cell">{Number(m.value).toFixed(2)} ({text(m.measureUnit)})</span> : '-';
    }},
    {title: '警告', dataIndex: 'warnings', render: (v) => v ? <Text type="warning">{v}</Text> : '-'},
    {title: '信号时间', dataIndex: 'signalDate', width: 170, render: datetime},
    ...(extraCol ? [extraCol] : []),
];

export default function SignalsPage() {
    const [params, setParams] = useSearchParams();
    const fundIdParam = params.get('fundId');
    const fundId = fundIdParam ? Number(fundIdParam) : null;
    const {data: funds} = useFunds();
    const {data: todaySignal, isLoading: todayLoading} = useSignalsToday(fundId);
    const [range, setRange] = useState(null);
    const from = range?.[0]?.format('YYYY-MM-DD');
    const to = range?.[1]?.format('YYYY-MM-DD');
    const {data: rangeSignals, isLoading: rangeLoading} = useSignalsRange(fundId, from, to);

    const fundOptions = (funds || []).map((f) => ({
        value: String(f.id),
        label: `${f.fundCode} · ${f.fundName}`,
    }));

    return (
        <Space direction="vertical" size={16} className="full-width">
            <Card title={<Title level={4}>今日信号</Title>}
                  extra={<Button icon={<ReloadOutlined/>} onClick={() => setParams({})}>清空筛选</Button>}>
                <Space style={{marginBottom: 16}}>
                    <Text type="secondary">基金：</Text>
                    <Select showSearch optionFilterProp="label" placeholder="选择基金"
                            value={fundIdParam || undefined} style={{width: 280}}
                            options={fundOptions} allowClear
                            onChange={(v) => setParams(v ? {fundId: v} : {})}/>
                </Space>
                {fundId ? (
                    <Table rowKey="id" size="small" loading={todayLoading}
                           dataSource={todaySignal ? [todaySignal] : []}
                           columns={signalColumns()} pagination={false}
                           locale={{emptyText: <EmptyState description="今日无信号"/>}}/>
                ) : (
                    <EmptyState description="选择基金查看今日信号"/>
                )}
            </Card>
            {fundId && (
                <Card title="历史信号查询">
                    <Space style={{marginBottom: 16}}>
                        <RangePicker value={range} onChange={setRange}/>
                    </Space>
                    <Table rowKey="id" size="small" loading={rangeLoading} dataSource={rangeSignals}
                           columns={signalColumns()} pagination={false}
                           locale={{emptyText: <EmptyState description="所选区间无信号"/>}}/>
                </Card>
            )}
        </Space>
    );
}
