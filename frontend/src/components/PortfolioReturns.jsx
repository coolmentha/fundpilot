import {Alert, Card, Col, Row, Skeleton, Statistic, Table, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {usePortfolioReturns} from '../api/hooks.js';
import {money, percent, pnlColor, signedMoney} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

const {Text} = Typography;

export default function PortfolioReturns() {
    const {data, isLoading, isError, refetch} = usePortfolioReturns();
    if (isLoading && !data) return <Card><Skeleton active paragraph={{rows: 3}}/></Card>;
    if (isError) return <Card><QueryErrorState onRetry={refetch} description="累计收益加载失败"/></Card>;

    const columns = [
        {title: '基金', dataIndex: 'fundName', ellipsis: true,
            render: (value, row) => <Link to={`/funds/${row.fundId}`}>{value}</Link>},
        {title: '投入', dataIndex: 'investedAmount', align: 'right', render: money},
        {title: '赎回净额', dataIndex: 'redeemedAmount', align: 'right', render: money},
        {title: '手续费', dataIndex: 'feeAmount', align: 'right', render: money},
        {title: '已实现', dataIndex: 'realizedPnl', align: 'right',
            render: (value) => <span style={{color: pnlColor(value)}}>{signedMoney(value)}</span>},
        {title: '未实现', dataIndex: 'unrealizedPnl', align: 'right',
            render: (value) => <span style={{color: pnlColor(value)}}>{signedMoney(value)}</span>},
        {title: '累计收益', dataIndex: 'totalReturn', align: 'right',
            render: (value) => <span style={{color: pnlColor(value)}}>{signedMoney(value)}</span>},
        {title: '收益率', dataIndex: 'returnRate', align: 'right', render: percent},
    ];

    return (
        <Card title="累计收益">
            {!data?.realizedComplete && <Alert type="warning" showIcon style={{marginBottom: 16}}
                message="部分历史卖出缺少完整 FIFO 成本，已实现收益暂不可用"/>}
            <Row gutter={[16, 16]} style={{marginBottom: 16}}>
                <Col xs={12} md={6}><Statistic title="累计总收益" value={data?.totalReturn}
                    formatter={(value) => signedMoney(value)} valueStyle={{color: pnlColor(data?.totalReturn)}}/></Col>
                <Col xs={12} md={6}><Statistic title="已实现收益" value={data?.realizedPnl}
                    formatter={(value) => signedMoney(value)} valueStyle={{color: pnlColor(data?.realizedPnl)}}/></Col>
                <Col xs={12} md={6}><Statistic title="未实现收益" value={data?.unrealizedPnl}
                    formatter={(value) => signedMoney(value)} valueStyle={{color: pnlColor(data?.unrealizedPnl)}}/></Col>
                <Col xs={12} md={6}><Statistic title="累计收益率" value={data?.returnRate}
                    formatter={(value) => percent(value)} valueStyle={{color: pnlColor(data?.returnRate)}}/></Col>
            </Row>
            <Text type="secondary">累计投入 {money(data?.investedAmount)} · 赎回净额 {money(data?.redeemedAmount)} · 手续费 {money(data?.feeAmount)}</Text>
            <Table rowKey="fundId" size="small" style={{marginTop: 16}} dataSource={data?.funds || []}
                   columns={columns} pagination={false} scroll={{x: 980}}/>
        </Card>
    );
}
