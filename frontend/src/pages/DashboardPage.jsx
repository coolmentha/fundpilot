import {Card, Col, Row, Space, Statistic, Table, Typography, Button, Empty} from 'antd';
import {Link, useNavigate} from 'react-router-dom';
import {ThunderboltOutlined, FundOutlined, WalletOutlined, PieChartOutlined} from '@ant-design/icons';
import {useFunds, usePendingSignals, useUserConfig} from '../api/hooks.js';
import {datetime, money, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';

const {Title} = Typography;

export default function DashboardPage() {
    const navigate = useNavigate();
    const {data: funds} = useFunds();
    const {data: pending} = usePendingSignals();
    const {data: config} = useUserConfig();

    const holdingFunds = (funds || []).filter((f) => f.status === 'HOLDING');
    const plannedTotal = holdingFunds.reduce((s, f) => s + Number(f.plannedTotalAmount || 0), 0);
    const capital = Number(config?.totalInvestableCapital || 0);
    const usageRatio = capital > 0 ? plannedTotal / capital : 0;
    const pendingCount = pending?.length ?? 0;

    const fundName = (id) => funds?.find((f) => f.id === id)?.fundName || `基金 #${id}`;

    const pendingColumns = [
        {title: '基金', width: 160, render: (_, r) => fundName(r.fundEntity?.id)},
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
        {title: '计划仓位', dataIndex: 'plannedTotalAmount', width: 140, align: 'right',
            render: (v) => <span className="num-cell">{money(v)}</span>},
        {
            title: '', width: 90, render: (_, r) => (
                <Link to={`/funds/${r.id}`}>详情</Link>
            ),
        },
    ];

    return (
        <Space direction="vertical" size={16} className="full-width">
            {/* KPI 概览 */}
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
                <Col xs={12} md={6}>
                    <Card className="kpi-card kpi-blue">
                        <Statistic title={<span className="kpi-label">总可投资金</span>}
                                   value={capital} prefix={<WalletOutlined/>}
                                   formatter={(v) => money(v)}/>
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card className="kpi-card kpi-violet">
                        <Statistic title={<span className="kpi-label">计划仓位占比</span>}
                                   value={usageRatio * 100} precision={1} suffix="%"
                                   prefix={<PieChartOutlined/>}/>
                    </Card>
                </Col>
            </Row>

            {/* 待确认信号 */}
            <Card title={<Title level={4}>待确认操作</Title>}
                  extra={pendingCount > 0 ? <Link to="/confirm">全部 →</Link> : null}>
                <Table rowKey="id" size="small" dataSource={pending || []} columns={pendingColumns}
                       pagination={false} scroll={{x: 760}}
                       locale={{emptyText: <EmptyState description="暂无待确认信号"/>}}/>
            </Card>

            {/* 持仓基金 */}
            <Card title={<Title level={4}>持仓基金</Title>}
                  extra={<Link to="/funds">全部基金 →</Link>}>
                <Table rowKey="id" size="small" dataSource={holdingFunds} columns={holdingColumns}
                       pagination={false} scroll={{x: 600}}
                       locale={{emptyText: <EmptyState description="暂无持仓基金"/>}}/>
            </Card>
        </Space>
    );
}
