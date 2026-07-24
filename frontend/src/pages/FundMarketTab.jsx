import {Card, Tabs} from 'antd';
import KlineChart from '../components/KlineChart.jsx';
import FundIntradayChart from '../components/FundIntradayChart.jsx';

/** 基金详情行情：当日分时与 K 线 / 净值走势图。 */
export default function MarketTab({fundId, fundSubType}) {
    return (
        <Tabs defaultActiveKey="intraday" items={[
            {key: 'intraday', label: '今日分时', children: <Card><FundIntradayChart fundId={fundId}/></Card>},
            {key: 'kline', label: 'K线 / 走势图', children: <Card><KlineChart fundId={fundId} fundSubType={fundSubType}/></Card>},
        ]}/>
    );
}
