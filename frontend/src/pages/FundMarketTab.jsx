import {Card, Descriptions, Skeleton, Typography} from 'antd';
import {useMarketIndicatorsToday} from '../api/hooks.js';
import {date, percent} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';
import KlineChart from '../components/KlineChart.jsx';

const {Title} = Typography;

const boolText = (v) => v ? '是' : '否';

/**
 * 基金详情 · 行情 tab。
 *
 * <p>两部分:
 * <ol>
 *   <li>K 线/走势图(行情工作台新增):ETF/指数基金渲染蜡烛图 + 成交量,
 *       主动/混合基金渲染累计净值折线图。日/周/月 K 可切换(仅 ETF)。</li>
 *   <li>今日行情指标快照(信号引擎用,每日 14:50 落库)。</li>
 * </ol>
 */
export default function MarketTab({fundId, fundSubType}) {
    const {data: snapshot, isLoading} = useMarketIndicatorsToday(fundId);

    return (
        <>
            <Card title={<Title level={5}>K 线 / 走势图</Title>} style={{marginBottom: 16}}>
                <KlineChart fundId={fundId} fundSubType={fundSubType}/>
            </Card>

            <Card title={<Title level={5}>今日行情指标 · {snapshot ? date(snapshot.snapshotDate) : '-'}</Title>}>
                {isLoading ? <Skeleton active paragraph={{rows: 4}}/> :
                    !snapshot ? <EmptyState description="今日暂无行情快照(每日 14:50 落库)"/> : (
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
                    )}
            </Card>
        </>
    );
}
