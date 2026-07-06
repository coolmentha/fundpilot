import {Card, Col, Row, Space, Statistic, Table, Typography, Button, Empty, Skeleton} from 'antd';
import {Link, useNavigate} from 'react-router-dom';
import {ThunderboltOutlined, FundOutlined, WalletOutlined,
    RiseOutlined, FallOutlined, SmileOutlined, FrownOutlined} from '@ant-design/icons';
import {useFunds, usePendingSignals, usePortfolioSummary} from '../api/hooks.js';
import {datetime, money, text, signedMoney, pnlColor} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';

const {Title} = Typography;

export default function DashboardPage() {
    const navigate = useNavigate();
    const {data: funds, isLoading: fundsLoading} = useFunds();
    const {data: pending, isLoading: pendingLoading} = usePendingSignals();
    const {data: summary, isLoading: summaryLoading} = usePortfolioSummary();

    const holdingFunds = (funds || []).filter((f) => f.status === 'HOLDING');
    const pendingCount = pending?.length ?? 0;
    const dailyPnlTotal = summary?.dailyPnlTotal;

    const fundName = (id) => funds?.find((f) => f.id === id)?.fundName || `基金 #${id}`;

    const pendingColumns = [
        {title: '基金', width: 160, render: (_, r) => fundName(r.fundId)},
        {title: '类型', dataIndex: 'signalType', width: 90, render: (v) => <StatusTag value={v}/>},
        {title: '档位', dataIndex: 'triggerTier', width: 70, render: (v) => v ?? '-'},
        {title: '建议量', width: 130, render: (_, r) => {
            const m = r.suggestedMeasure;
            return m ? <span className="num-cell">{Number(m.value).toFixed(2)} ({text(m.measureUnit)})</span> : '-';
        }},
        {title: '信号时间', dataIndex: 'signalDate', width: 170, render: datetime},
        {
            title: '', width: 100, render: (_, r) => r.signalType !== 'NONE' && (
                <Button type="primary" size="small" onClick={() => navigate('/confirm')}>去确认</Button>
            ),
        },
    ];

    const holdingColumns = [
        {title: '代码', dataIndex: 'fundCode', width: 110},
        {title: '名称', dataIndex: 'fundName', ellipsis: true},
        {title: '类型', dataIndex: 'fundCategory', width: 90, render: (v) => <StatusTag value={v}/>},
        {
            title: '', width: 90, render: (_, r) => (
                <Link to={`/funds/${r.id}`}>详情</Link>
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
                    <Card className="kpi-card kpi-amber" onClick={() => navigate('/confirm')}
                          hoverable style={{cursor: 'pointer'}}>
                        <Statistic title={<span className="kpi-label">待确认操作</span>}
                                   value={pendingCount} prefix={<ThunderboltOutlined/>}/>
                    </Card>
                </Col>
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
                                   formatter={(v) => (
                                       <span style={{color: pnlColor(dailyPnlTotal)}}>
                                           {signedMoney(v)}
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

            {/* 待确认信号 */}
            <Card title={<Title level={4}>待确认操作</Title>}
                  extra={pendingCount > 0 ? <Link to="/confirm">全部 →</Link> : null}>
                <Table rowKey="id" size="small" loading={pendingLoading} dataSource={pending || []} columns={pendingColumns}
                       pagination={false} scroll={{x: 760}}
                       locale={{emptyText: <EmptyState description="暂无待确认信号"/>}}/>
            </Card>

            {/* 持仓基金 */}
            <Card title={<Title level={4}>持仓基金</Title>}
                  extra={<Link to="/funds">全部基金 →</Link>}>
                <Table rowKey="id" size="small" loading={fundsLoading} dataSource={holdingFunds} columns={holdingColumns}
                       pagination={false} scroll={{x: 600}}
                       locale={{emptyText: <EmptyState description="暂无持仓基金"/>}}/>
            </Card>
        </Space>
    );
}
