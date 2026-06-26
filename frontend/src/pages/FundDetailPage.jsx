import {Card, Descriptions, Skeleton, Space, Tabs, Typography, Button} from 'antd';
import {Link, useParams} from 'react-router-dom';
import {ArrowLeftOutlined} from '@ant-design/icons';
import {useFund} from '../api/hooks.js';
import {money, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import StrategyTab from './FundStrategyTab.jsx';
import SignalTab from './FundSignalTab.jsx';
import MarketTab from './FundMarketTab.jsx';

const {Title} = Typography;

/**
 * 基金详情页：聚合策略 / 信号 / 行情三个 tab，替代独立 /funds/:id/strategies 路由。
 * 顶部展示基金档案，编辑仍在基金管理页进行。
 */
export default function FundDetailPage() {
    const {fundId} = useParams();
    const id = Number(fundId);
    const {data: fund, isLoading} = useFund(id);

    if (isLoading) return <Card><Skeleton active paragraph={{rows: 6}}/></Card>;
    if (!fund) return <Card><Title level={4}>基金不存在</Title></Card>;

    const items = [
        {key: 'strategy', label: '策略参数', children: <StrategyTab fundId={id}/>},
        {key: 'signal', label: '交易信号', children: <SignalTab fundId={id}/>},
        {key: 'market', label: '行情指标', children: <MarketTab fundId={id}/>},
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
                <Descriptions.Item label="计划仓位">
                    <span className="num-cell">{money(fund.plannedTotalAmount)}</span>
                </Descriptions.Item>
                <Descriptions.Item label="跟踪指数">{text(fund.benchmarkIndexCode)}</Descriptions.Item>
            </Descriptions>
            <Tabs defaultActiveKey="strategy" items={items}/>
        </Card>
    );
}
