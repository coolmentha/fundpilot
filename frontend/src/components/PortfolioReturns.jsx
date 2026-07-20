import {useState} from 'react';
import {Alert, Col, DatePicker, Row, Segmented, Skeleton, Statistic, Table, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {usePortfolioReturns, usePortfolioReturnTrends} from '../api/hooks.js';
import {date, money, percent, pnlColor, signedMoney} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

const {Text} = Typography;

export default function PortfolioReturns() {
    const [period, setPeriod] = useState('30D');
    const [customRange, setCustomRange] = useState([null, null]);
    const {data, isLoading, isError, refetch} = usePortfolioReturns();
    const {data: trend} = usePortfolioReturnTrends(period, customRange[0], customRange[1]);
    if (isLoading && !data) return <Skeleton active paragraph={{rows: 3}}/>;
    if (isError) return <QueryErrorState onRetry={refetch} description="累计收益加载失败"/>;

    const columns = [
        {title: '基金', dataIndex: 'fundName', ellipsis: true,
            render: (value, row) => <Link to={`/funds/${row.fundId}`}>{value}</Link>},
        {title: '投入', dataIndex: 'investedAmount', align: 'right', render: money},
        {title: '赎回净额', dataIndex: 'redeemedAmount', align: 'right', render: money},
        {title: '手续费', dataIndex: 'feeAmount', align: 'right', render: money},
        {title: '已实现盈亏', dataIndex: 'realizedPnl', align: 'right',
            render: (value) => <span style={{color: pnlColor(value)}}>{signedMoney(value)}</span>},
        {title: '浮动盈亏', dataIndex: 'unrealizedPnl', align: 'right',
            render: (value) => <span style={{color: pnlColor(value)}}>{signedMoney(value)}</span>},
        {title: '累计收益', dataIndex: 'totalReturn', align: 'right',
            render: (value) => <span style={{color: pnlColor(value)}}>{signedMoney(value)}</span>},
        {title: '收益率', dataIndex: 'returnRate', align: 'right', render: percent},
    ];

    return (
        <div>
            {!data?.realizedComplete && <Alert type="warning" showIcon style={{marginBottom: 16}}
                message="部分历史卖出缺少完整 FIFO 成本，已实现盈亏暂不可用"/>}
            <Row gutter={[16, 16]} style={{marginBottom: 16}}>
                <Col xs={12} md={6}><Statistic title="累计总收益" value={data?.totalReturn}
                    formatter={(value) => signedMoney(value)} valueStyle={{color: pnlColor(data?.totalReturn)}}/></Col>
                <Col xs={12} md={6}><Statistic title="已实现盈亏" value={data?.realizedPnl}
                    formatter={(value) => signedMoney(value)} valueStyle={{color: pnlColor(data?.realizedPnl)}}/></Col>
                <Col xs={12} md={6}><Statistic title="浮动盈亏" value={data?.unrealizedPnl}
                    formatter={(value) => signedMoney(value)} valueStyle={{color: pnlColor(data?.unrealizedPnl)}}/></Col>
                <Col xs={12} md={6}><Statistic title="累计收益率" value={data?.returnRate}
                    formatter={(value) => percent(value)} valueStyle={{color: pnlColor(data?.returnRate)}}/></Col>
            </Row>
            <Text type="secondary">累计投入 {money(data?.investedAmount)} · 赎回净额 {money(data?.redeemedAmount)} · 手续费 {money(data?.feeAmount)}</Text>
            <div className="portfolio-trend-header">
                <Segmented value={period} options={[
                    {label: '今日', value: 'TODAY'}, {label: '近 7 日', value: '7D'},
                    {label: '近 30 日', value: '30D'}, {label: '今年以来', value: 'YTD'},
                ]} onChange={(value) => { setPeriod(value); setCustomRange([null, null]); }}/>
                <DatePicker.RangePicker allowClear onChange={(values) => {
                    if (!values || !values[0] || !values[1]) return setCustomRange([null, null]);
                    setCustomRange(values.map((value) => `${value.format('YYYY-MM-DD')}T00:00:00Z`));
                }}/>
            </div>
            {!trend?.valuationComplete && <Alert type="warning" showIcon style={{marginTop: 12}}
                message={`${trend?.missingFundCodes?.length || 0} 只基金净值未覆盖本区间，曲线按可用净值计算`}/>}
            {trend?.points?.length ? <>
                {!trend.dataSufficient && <Alert type="info" showIcon style={{marginTop: 12}}
                    message={`历史数据从 ${date(trend.dataStartDate)} 开始，当前区间暂无完整基线`}/>}
                <Row gutter={[16, 12]} className="portfolio-trend-stats">
                    <Col xs={12} md={6}><Statistic title="区间收益" value={trend.intervalReturn}
                        formatter={signedMoney} valueStyle={{color: pnlColor(trend.intervalReturn)}}/></Col>
                    <Col xs={12} md={6}><Statistic title="区间收益率" value={trend.intervalReturnRate}
                        formatter={percent} valueStyle={{color: pnlColor(trend.intervalReturnRate)}}/></Col>
                    <Col xs={12} md={6}><Statistic title="区间投入 / 赎回"
                        value={`${money(trend.investedAmount)} / ${money(trend.redeemedAmount)}`}/></Col>
                    <Col xs={12} md={6}><Statistic title="最大回撤" value={trend.maximumDrawdown}
                        formatter={money}/></Col>
                </Row>
                <ReturnTrendChart points={trend.points}/>
            </> : <div className="portfolio-trend-empty">趋势数据将在首个净值确认快照后显示</div>}
            <Table rowKey="fundId" size="small" style={{marginTop: 16}} dataSource={data?.funds || []}
                   columns={columns} pagination={false} scroll={{x: 980}}/>
        </div>
    );
}

function ReturnTrendChart({points}) {
    const values = points.map((point) => Number(point.totalReturn));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const coords = values.map((value, index) => {
        const x = points.length === 1 ? 50 : index * 100 / (points.length - 1);
        return `${x},${92 - (value - min) * 78 / span}`;
    }).join(' ');
    return <div className="portfolio-trend-chart" aria-label="组合累计收益趋势">
        <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img">
            <line x1="0" y1="92" x2="100" y2="92" className="trend-axis"/>
            <polyline points={coords} className="trend-line"/>
        </svg>
        <div className="portfolio-trend-dates"><span>{date(points[0].date)}</span><span>{date(points.at(-1).date)}</span></div>
    </div>;
}
