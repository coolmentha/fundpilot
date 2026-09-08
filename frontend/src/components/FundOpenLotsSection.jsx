import {Card, Descriptions, Skeleton, Table, Typography} from 'antd';
import {date, money, percent} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

const {Text} = Typography;
const unavailableReasons = {
    NO_OPEN_LOTS: '暂无未结清批次',
    LATEST_NAV_MISSING: '缺少最新单位净值',
    REDEMPTION_FEE_MISSING: '缺少适用赎回费率',
};
const reason = (value) => value ? (unavailableReasons[value] || value) : '-';
const amount = (value) => value == null ? '-' : money(value);
const number = (value, digits = 2) => value == null ? '-' : Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
});

const columns = [
    {title: '取得日期', dataIndex: 'acquireDate', render: date},
    {title: '持有天数', dataIndex: 'holdingDays', align: 'right', render: (value) => value == null ? '-' : `${value} 天`},
    {title: '剩余份额', dataIndex: 'remainingShares', align: 'right', render: (value) => number(value)},
    {title: '适用赎回费率', dataIndex: 'redemptionRate', align: 'right', render: percent},
    {title: '赎回费估算', dataIndex: 'estimatedRedemptionFee', align: 'right', render: amount},
    {title: '未知原因', dataIndex: 'unavailableReason', render: reason},
];

export default function FundOpenLotsSection({query}) {
    if (query.isLoading) return <Card size="small" title="持仓批次与赎回费估算" style={{marginBottom: 16}}><Skeleton active paragraph={{rows: 2}}/></Card>;
    if (query.isError) return <Card size="small" title="持仓批次与赎回费估算" style={{marginBottom: 16}}>
        <QueryErrorState onRetry={query.refetch} description="持仓批次加载失败"/>
    </Card>;

    const data = query.data;
    if (!data) return <Card size="small" title="持仓批次与赎回费估算" style={{marginBottom: 16}}><Text type="secondary">暂无批次数据</Text></Card>;
    const rows = (data.lots || []).map((lot, index) => ({...lot, key: index}));

    return (
        <Card size="small" title="持仓批次与赎回费估算" style={{marginBottom: 16}}>
            <Descriptions column={{xs: 1, sm: 3}} size="small" style={{marginBottom: 12}}>
                <Descriptions.Item label="估算净值日期">{date(data.latestNavDate)}</Descriptions.Item>
                <Descriptions.Item label="最新单位净值">{number(data.latestUnitNav, 4)}</Descriptions.Item>
                <Descriptions.Item label="赎回费估算合计">{amount(data.estimatedRedemptionFee)}</Descriptions.Item>
                {data.unavailableReason && <Descriptions.Item label="无法估算原因">{reason(data.unavailableReason)}</Descriptions.Item>}
            </Descriptions>
            <Table size="small" rowKey="key" dataSource={rows} columns={columns} pagination={false}
                   locale={{emptyText: reason(data.unavailableReason)}} scroll={{x: 760}}/>
        </Card>
    );
}
