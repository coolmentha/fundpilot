import {Card, Descriptions, Skeleton, Space, Tabs, Typography, Button} from 'antd';
import {Link, useParams} from 'react-router-dom';
import {ArrowLeftOutlined} from '@ant-design/icons';
import {useFund, useFundFeeRates} from '../api/hooks.js';
import {date, money, text, signedMoney, signedPercent, pnlColor} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import StrategyTab from './FundStrategyTab.jsx';
import SignalTab from './FundSignalTab.jsx';
import MarketTab from './FundMarketTab.jsx';
import FundTransactionTab from './FundTransactionTab.jsx';
import FundDcaTab from './FundDcaTab.jsx';
import QueryErrorState from '../components/QueryErrorState.jsx';
import {estimateStatusText} from '../querySafety.js';

const {Title, Text} = Typography;

/**
 * 基金详情页：聚合策略 / 信号 / 行情三个 tab，替代独立 /funds/:id/strategies 路由。
 * 顶部展示基金档案，编辑仍在基金管理页进行。
 */
export default function FundDetailPage() {
    const {fundId} = useParams();
    const id = Number(fundId);
    const {data: fund, isLoading, isError, refetch} = useFund(id);
    const {data: feeRates} = useFundFeeRates(id);

    if (isLoading) return <Card><Skeleton active paragraph={{rows: 6}}/></Card>;
    if (isError) return <Card><QueryErrorState onRetry={refetch} description="基金详情加载失败"/></Card>;
    if (!fund) return <Card><Title level={4}>基金不存在</Title></Card>;

    const items = [
        {key: 'transaction', label: '交易流水', children: <FundTransactionTab fundId={id}/>},
        {key: 'strategy', label: '策略参数', children: <StrategyTab fundId={id}/>},
        {key: 'signal', label: '交易信号', children: <SignalTab fundId={id}/>},
        {key: 'market', label: '行情指标', children: <MarketTab fundId={id} fundSubType={fund.fundSubType}/>},
        {key: 'dca', label: '定投计划', children: <FundDcaTab fundId={id}/>},
    ];

    return (
        <Card title={
            <Space>
                <Link to="/funds"><Button type="text" icon={<ArrowLeftOutlined/>}/></Link>
                <Title level={4} style={{margin: 0}}>{fund.fundName}</Title>
                <Text type="secondary" className="num-cell">{fund.fundCode}</Text>
            </Space>
        }>
            <Descriptions column={{xs: 1, sm: 2, md: 3}} size="small" style={{marginBottom: 16}}>
                <Descriptions.Item label="类型"><StatusTag value={fund.fundCategory}/></Descriptions.Item>
                <Descriptions.Item label="子类">{text(fund.fundSubType)}</Descriptions.Item>
                <Descriptions.Item label="状态"><StatusTag value={fund.status}/></Descriptions.Item>
                <Descriptions.Item label="成本单价">
                    <span className="num-cell">
                        {fund.costPerShare === null || fund.costPerShare === undefined ? '-' : money(fund.costPerShare)}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label="今日涨跌">
                    {estimateStatusText(fund.estimateStatus)
                        ? <span className={fund.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{estimateStatusText(fund.estimateStatus)}</span>
                        : <span style={{color: pnlColor(fund.dailyChangePct)}}>
                            {signedPercent(fund.dailyChangePct)}
                            {fund.isEstimated && <span className="estimate-tag">估</span>}
                        </span>}
                </Descriptions.Item>
                <Descriptions.Item label="持仓市值">
                    <span className="num-cell">
                        {fund.holdingAmount === null || fund.holdingAmount === undefined ? '-' : money(fund.holdingAmount)}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label="持仓份额">
                    <span className="num-cell">
                        {fund.holdingShares === null || fund.holdingShares === undefined
                            ? '-'
                            : Number(fund.holdingShares).toLocaleString('zh-CN', {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2,
                            })}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label="今日盈亏">
                    {estimateStatusText(fund.estimateStatus)
                        ? <span className={fund.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{estimateStatusText(fund.estimateStatus)}</span>
                        : <span style={{color: pnlColor(fund.dailyPnl)}}>{signedMoney(fund.dailyPnl)}</span>}
                </Descriptions.Item>
                <Descriptions.Item label="总盈亏">
                    <span style={{color: pnlColor(fund.totalPnl)}}>{signedMoney(fund.totalPnl)}</span>
                </Descriptions.Item>
                <Descriptions.Item label="收益计算依据" span={2}>
                    {fund.valuationSource === 'INTRADAY_ESTIMATE'
                        ? `盘中估值${fund.estimateTime ? `（${fund.estimateTime}）` : ''}，基准净值日 ${fund.baseNavDate || '-'}，计算净值 ${fund.valuationNav == null ? '-' : money(fund.valuationNav)}`
                        : fund.valuationSource === 'CONFIRMED_NAV'
                            ? `当日已确认净值（${date(fund.valuationDate)}），计算净值 ${fund.valuationNav == null ? '-' : money(fund.valuationNav)}`
                            : fund.valuationSource === 'LATEST_CONFIRMED_NAV'
                                ? `${fund.investmentTarget === 'QDII' ? '最新确认净值' : '估值不可用，使用最近确认净值'}（${date(fund.valuationDate)}），计算净值 ${fund.valuationNav == null ? '-' : money(fund.valuationNav)}`
                                : <span className="muted">暂无可用净值</span>}
                </Descriptions.Item>
                <Descriptions.Item label="跟踪指数">{text(fund.benchmarkIndexCode)}</Descriptions.Item>
                <Descriptions.Item label="参考费率">
                    {feeRates && feeRates.discountRate != null
                        ? <span className="num-cell">申购 {(Number(feeRates.discountRate)*100).toFixed(2)}%</span>
                        : <span className="muted">未爬取</span>}
                </Descriptions.Item>
            </Descriptions>
            <Tabs defaultActiveKey="transaction" items={items}/>
        </Card>
    );
}
