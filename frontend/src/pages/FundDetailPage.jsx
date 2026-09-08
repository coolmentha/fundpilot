import {Alert, Button, Card, Descriptions, Skeleton, Space, Tabs, Tag, Typography} from 'antd';
import {Link, useParams} from 'react-router-dom';
import {ArrowLeftOutlined, EditOutlined} from '@ant-design/icons';
import {useFund, useFundFeeRates, useFundOpenLots, useFundResearch, usePendingSignals, usePendingTransactions} from '../api/hooks.js';
import {date, datetime, money, percent, text, signedMoney, signedPercent, pnlColor} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import StrategyTab from './FundStrategyTab.jsx';
import SignalTab from './FundSignalTab.jsx';
import MarketTab from './FundMarketTab.jsx';
import FundTransactionTab from './FundTransactionTab.jsx';
import FundDcaTab from './FundDcaTab.jsx';
import QueryErrorState from '../components/QueryErrorState.jsx';
import {estimateStatusText} from '../querySafety.js';
import {redemptionLadderText} from '../feeRates.js';
import FundResearchSection from '../components/FundResearchSection.jsx';
import FundOpenLotsSection from '../components/FundOpenLotsSection.jsx';

const {Title, Text} = Typography;

/**
 * 基金详情页：聚合策略 / 建议 / 行情三个 tab，替代独立 /funds/:id/strategies 路由。
 * 顶部展示基金档案，编辑仍在基金管理页进行。
 */
export default function FundDetailPage() {
    const {portfolioFundId: portfolioFundIdParam} = useParams();
    const portfolioFundId = Number(portfolioFundIdParam);
    const {data: fund, isLoading, isError, refetch} = useFund(portfolioFundId);
    const feeRatesQuery = useFundFeeRates(fund?.fundCode);
    const {data: feeRates} = feeRatesQuery;
    const researchQuery = useFundResearch(fund?.fundCode);
    const openLotsQuery = useFundOpenLots(portfolioFundId);
    const {data: pendingTransactions} = usePendingTransactions();
    const {data: pendingSignals} = usePendingSignals();

    if (isLoading) return <Card><Skeleton active paragraph={{rows: 6}}/></Card>;
    if (isError) return <Card><QueryErrorState onRetry={refetch} description="基金详情加载失败"/></Card>;
    if (!fund) return <Card><Title level={4}>基金不存在</Title></Card>;

    const pendingTransactionCount = pendingTransactions?.filter(
        (transaction) => transaction.portfolioFundId === portfolioFundId,
    ).length ?? 0;
    const pendingSignalCount = pendingSignals?.filter(
        (signal) => signal.portfolioFundId === portfolioFundId,
    ).length ?? 0;
    const redemptionRates = redemptionLadderText(feeRates?.redemptionLadder);
    const holdingAmount = Number(fund.holdingAmount);
    const totalPnl = Number(fund.totalPnl);
    const totalPnlRate = fund.holdingAmount == null || fund.totalPnl == null
        || !Number.isFinite(holdingAmount) || !Number.isFinite(totalPnl)
        || holdingAmount - totalPnl <= 0
        ? null
        : totalPnl / (holdingAmount - totalPnl);
    const items = [
        {key: 'market', label: '行情指标', children: <MarketTab portfolioFundId={fund.portfolioFundId} fundSubType={fund.fundSubType}/>},
        {key: 'transaction', label: '交易流水', children: <FundTransactionTab portfolioFundId={portfolioFundId}/>},
        {key: 'strategy', label: '策略参数', children: <StrategyTab portfolioFundId={portfolioFundId}/>},
        {key: 'advice', label: '纪律建议', children: <SignalTab portfolioFundId={portfolioFundId}/>},
        {key: 'dca', label: '定投计划', children: <FundDcaTab portfolioFundId={fund.portfolioFundId}
                                                               benchmarkIndexCode={fund.benchmarkIndexCode}/>},
    ];

    return (
        <Card className="fund-detail" title={
            <Space>
                <Link to="/funds"><Button type="text" icon={<ArrowLeftOutlined/>}/></Link>
                <Title level={4} style={{margin: 0}}>{fund.fundName}</Title>
                <Text type="secondary" className="num-cell">{fund.fundCode}</Text>
            </Space>
        } extra={<Link to={`/funds?editPortfolioFundId=${portfolioFundId}`}><Button icon={<EditOutlined/>}>编辑基金</Button></Link>}>
            {(pendingTransactionCount > 0 || pendingSignalCount > 0) && (
                <Alert type="warning" showIcon style={{marginBottom: 16}}
                       title="有待处理事项"
                       description={<Space wrap>
                           {pendingTransactionCount > 0 && (
                               <Link to={`/confirm?portfolioFundId=${portfolioFundId}`}>待确认交易 {pendingTransactionCount} 笔</Link>
                           )}
                           {pendingSignalCount > 0 && (
                               <Link to={`/advice?portfolioFundId=${portfolioFundId}`}>待回应建议 {pendingSignalCount} 条</Link>
                           )}
                       </Space>}/>
            )}
            <Descriptions className="fund-detail-summary" column={{xs: 1, sm: 2, md: 3}} size="small" style={{marginBottom: 16}}>
                <Descriptions.Item label="类型"><StatusTag value={fund.fundCategory}/></Descriptions.Item>
                <Descriptions.Item label="子类">{text(fund.fundSubType)}</Descriptions.Item>
                <Descriptions.Item label="状态"><StatusTag value={fund.status}/></Descriptions.Item>
                <Descriptions.Item label="持仓市值">
                    <span className="num-cell">
                        {fund.holdingAmount === null || fund.holdingAmount === undefined ? '-' : money(fund.holdingAmount)}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label="总盈亏">
                    <span style={{color: pnlColor(fund.totalPnl)}}>
                        {signedMoney(fund.totalPnl)}{' '}
                        <span className="num-cell">({signedPercent(totalPnlRate)})</span>
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label="今日盈亏">
                    {estimateStatusText(fund.estimateStatus)
                        ? <span className={fund.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{estimateStatusText(fund.estimateStatus)}</span>
                        : <span style={{color: pnlColor(fund.dailyPnl)}}>
                            {signedMoney(fund.dailyPnl)}{' '}
                            <span className="num-cell">({signedPercent(fund.dailyChangePct)})</span>
                            {fund.isEstimated && <span className="estimate-tag">估</span>}
                        </span>}
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
                <Descriptions.Item label="成本单价">
                    <span className="num-cell">
                        {fund.costPerShare === null || fund.costPerShare === undefined ? '-' : money(fund.costPerShare)}
                    </span>
                </Descriptions.Item>
                <Descriptions.Item label="收益计算依据">
                    {fund.valuationSource === 'INTRADAY_ESTIMATE'
                        ? `盘中估值${fund.estimateTime ? `（${fund.estimateTime}）` : ''}，基准净值日 ${fund.baseNavDate || '-'}，计算净值 ${fund.valuationNav == null ? '-' : money(fund.valuationNav)}`
                        : fund.valuationSource === 'CONFIRMED_NAV'
                            ? `当日已确认净值（${date(fund.valuationDate)}），计算净值 ${fund.valuationNav == null ? '-' : money(fund.valuationNav)}`
                            : fund.valuationSource === 'LATEST_CONFIRMED_NAV'
                                ? `${fund.investmentTarget === 'QDII' ? '最新确认净值' : '估值不可用，使用最近确认净值'}（${date(fund.valuationDate)}）${fund.investmentTarget === 'QDII' && fund.valuationFirstSeenAt ? `，平台发现于 ${datetime(fund.valuationFirstSeenAt)}` : ''}，计算净值 ${fund.valuationNav == null ? '-' : money(fund.valuationNav)}`
                                : <span className="muted">暂无可用净值</span>}
                </Descriptions.Item>
                <Descriptions.Item label="跟踪指数">{text(fund.benchmarkIndexCode)}</Descriptions.Item>
                <Descriptions.Item label="参考费率">
                    {feeRates?.discountRate != null
                        ? <span className="num-cell">申购优惠 {percent(feeRates.discountRate)}</span>
                        : <span className="muted">未爬取</span>}
                </Descriptions.Item>
                <Descriptions.Item label="申购原费率">
                    {feeRates?.purchaseRate != null
                        ? <span className="num-cell">{percent(feeRates.purchaseRate)}</span>
                        : <span className="muted">未爬取</span>}
                </Descriptions.Item>
                <Descriptions.Item label="赎回费率">
                    {redemptionRates
                        ? <span className="num-cell">{redemptionRates}</span>
                        : <span className="muted">未爬取</span>}
                </Descriptions.Item>
                {feeRates?.salesServiceFee != null && (
                    <Descriptions.Item label="销售服务费（年化）">
                        <span className="num-cell">{percent(feeRates.salesServiceFee)}</span>
                    </Descriptions.Item>
                )}
                <Descriptions.Item label="管理费（年化）">
                    {feeRates?.managementFee == null ? <span className="muted">未爬取</span> : <span className="num-cell">{percent(feeRates.managementFee)}</span>}
                </Descriptions.Item>
                <Descriptions.Item label="托管费（年化）">
                    {feeRates?.custodyFee == null ? <span className="muted">未爬取</span> : <span className="num-cell">{percent(feeRates.custodyFee)}</span>}
                </Descriptions.Item>
                <Descriptions.Item label="申购状态">
                    {feeRates?.purchaseStatus == null ? <span className="muted">未知</span> : ({OPEN: '开放', SUSPENDED: '暂停', LIMITED: '限额', UNKNOWN: '未知'}[feeRates.purchaseStatus] || feeRates.purchaseStatus)}
                </Descriptions.Item>
                <Descriptions.Item label="单笔申购上限">
                    {feeRates?.purchaseLimit == null ? <span className="muted">未知</span> : <span className="num-cell">{money(feeRates.purchaseLimit)}</span>}
                </Descriptions.Item>
                <Descriptions.Item label="最低申购金额">
                    {feeRates?.minimumPurchaseAmount == null ? <span className="muted">未知</span> : <span className="num-cell">{money(feeRates.minimumPurchaseAmount)}</span>}
                </Descriptions.Item>
                <Descriptions.Item label="费率渠道">
                    {feeRatesQuery.isError
                        ? <span className="muted">天天基金渠道参考加载失败</span>
                        : feeRates
                            ? <Space wrap>{feeRates.channelReference?.label || '天天基金渠道参考'}{feeRates.channelReference?.sourceName && `（${feeRates.channelReference.sourceName}）`}{feeRates.status === 'FAILED' && <Tag color="red">采集失败</Tag>}{feeRates.stale && <Tag color="orange">数据已过期</Tag>}</Space>
                            : <span className="muted">未爬取</span>}
                </Descriptions.Item>
            </Descriptions>
            <FundResearchSection query={researchQuery}/>
            <FundOpenLotsSection query={openLotsQuery}/>
            <Tabs defaultActiveKey="market" items={items}/>
        </Card>
    );
}
