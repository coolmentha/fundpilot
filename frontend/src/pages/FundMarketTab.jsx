import {Card, Descriptions, Skeleton, Typography} from 'antd';
import {useMarketIndicatorsToday} from '../api/hooks.js';
import {date, percent, text} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';

const {Title} = Typography;

const boolText = (v) => v ? '是' : '否';

/**
 * 基金详情 · 行情 tab。展示当日 14:50 落库的行情指标快照（表级缓存）。
 * 字段对应信号引擎九步流程的行情输入，详见 CONTEXT.md「行情数据缓存」。
 */
export default function MarketTab({fundId}) {
    const {data: snapshot, isLoading} = useMarketIndicatorsToday(fundId);

    if (isLoading) return <Card><Skeleton active paragraph={{rows: 4}}/></Card>;
    if (!snapshot) return <Card><EmptyState description="今日暂无行情快照（每日 14:50 落库）"/></Card>;

    return (
        <Card title={<Title level={5}>今日行情指标 · {date(snapshot.snapshotDate)}</Title>}>
            <Descriptions column={{xs: 1, sm: 2, md: 3}} bordered size="small">
                <Descriptions.Item label="最近累计净值">
                    <span className="num-cell">{Number(snapshot.currentNav ?? 0).toFixed(4)}</span>
                </Descriptions.Item>
                <Descriptions.Item label="60 日新高">{boolText(snapshot.sixtyDayHigh)}</Descriptions.Item>
                <Descriptions.Item label="单周跌幅">
                    <span className="num-cell">{percent(snapshot.weeklyDropPercent)}</span>
                </Descriptions.Item>
                <Descriptions.Item label="价格在年线上方">{boolText(snapshot.priceAboveYearLine)}</Descriptions.Item>
                <Descriptions.Item label="年线向上">{boolText(snapshot.yearLineRising)}</Descriptions.Item>
                <Descriptions.Item label="周 MACD">
                    <StatusTag value={snapshot.weeklyMacdState}/>
                </Descriptions.Item>
                <Descriptions.Item label="成交量状态">
                    <StatusTag value={snapshot.volumeState}/>
                </Descriptions.Item>
            </Descriptions>
        </Card>
    );
}
