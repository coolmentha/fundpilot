import {Card, Col, Row, Space, Statistic, Table, Typography, Skeleton} from 'antd';
import {Link} from 'react-router-dom';
import {FundOutlined, WalletOutlined,
    RiseOutlined, FallOutlined, SmileOutlined} from '@ant-design/icons';
import {useFunds, usePortfolioSummary} from '../api/hooks.js';
import {signedMoney, pnlColor} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';

const {Title} = Typography;

export default function DashboardPage() {
    const {data: funds, isLoading: fundsLoading} = useFunds();
    const {data: summary, isLoading: summaryLoading} = usePortfolioSummary();

    const holdingFunds = (funds || []).filter((f) => f.status === 'HOLDING');
    const dailyPnlTotal = summary?.dailyPnlTotal;
    const estimateFetchFailedCount = summary?.estimateFetchFailedCount ?? 0;
    const estimateFetchFailed = estimateFetchFailedCount > 0;

    const holdingColumns = [
        {title: '代码', dataIndex: 'fundCode', width: 110},
        {title: '名称', dataIndex: 'fundName', ellipsis: true,
            render: (v, r) => <Link to={`/funds/${r.portfolioFundId}`}>{v}</Link>},
        {title: '类型', dataIndex: 'fundCategory', width: 90, render: (v) => <StatusTag value={v}/>},
        {
            title: '', width: 90, render: (_, r) => (
                <Link to={`/funds/${r.portfolioFundId}`}>详情</Link>
            ),
        },
    ];

    return (
        <Space direction="vertical" size={16} className="full-width">
            {/* KPI 概览(行情工作台转向后移除总可投资金/计划仓位占比卡片) */}
            {summaryLoading && !summary ? (
                <Card><Skeleton active paragraph={{rows: 2}}/></Card>
            ) : (
            <Row gutter={[16, 16]}>
                <Col xs={12} md={6}>
                    <Card className="kpi-card kpi-green">
                        <Statistic title={<span className="kpi-label">持仓基金</span>}
                                   value={holdingFunds.length} prefix={<FundOutlined/>}/>
                    </Card>
                </Col>
            </Row>
            )}

            {/* 盈亏视角 KPI(issue #18 概览页) */}
            <Row gutter={[16, 16]}>
                <Col xs={12} md={6}>
                    <Card className="kpi-card">
                        <Statistic title={<span className="kpi-label">今日盈亏合计</span>}
                                   value={dailyPnlTotal ?? 0}
                                   prefix={<WalletOutlined/>}
                                   formatter={() => estimateFetchFailed ? (
                                       <span className="estimate-failure">估值拉取失败</span>
                                   ) : dailyPnlTotal == null ? '-' : (
                                       <span style={{color: pnlColor(dailyPnlTotal)}}>
                                           {signedMoney(dailyPnlTotal)}
                                           {summary?.isEstimated && <span className="estimate-tag">估</span>}
                                       </span>
                                   )}/>
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card className="kpi-card kpi-red">
                        <Statistic title={<span className="kpi-label">上涨基金</span>}
                                   value={summary?.risingFundCount ?? 0} prefix={<RiseOutlined/>}/>
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card className="kpi-card kpi-green">
                        <Statistic title={<span className="kpi-label">下跌基金</span>}
                                   value={summary?.fallingFundCount ?? 0} prefix={<FallOutlined/>}/>
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card className="kpi-card">
                        <Statistic title={<span className="kpi-label">盈利 / 亏损</span>}
                                   value={`${summary?.profitableFundCount ?? 0} / ${summary?.losingFundCount ?? 0}`}
                                   prefix={<SmileOutlined/>}/>
                    </Card>
                </Col>
            </Row>

            {/* 持仓基金 */}
            <Card title={<Title level={4}>持仓基金</Title>}
                  extra={<Link to="/funds">全部基金 →</Link>}>
                <Table rowKey="portfolioFundId" size="small" loading={fundsLoading} dataSource={holdingFunds} columns={holdingColumns}
                       pagination={false} scroll={{x: 600}}
                       locale={{emptyText: <EmptyState description="暂无持仓基金"/>}}/>
            </Card>
        </Space>
    );
}
