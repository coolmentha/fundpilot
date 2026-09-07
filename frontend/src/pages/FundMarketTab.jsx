import {Alert, Button, Card, Tabs} from 'antd';
import {usePortfolioMarketRefresh} from '../api/portfolioMarket.js';
import KlineChart from '../components/KlineChart.jsx';
import FundIntradayChart from '../components/FundIntradayChart.jsx';

/** 基金详情行情：当日分时与 K 线 / 净值走势图。 */
export default function MarketTab({portfolioFundId, fundSubType}) {
    const refresh = usePortfolioMarketRefresh(portfolioFundId);
    return (
        <>
        <Button onClick={() => refresh.mutate()} loading={refresh.isPending} disabled={!portfolioFundId}>刷新行情</Button>
        {refresh.isError && <Alert type="error" showIcon title={refresh.error?.message || '行情刷新失败，请稍后重试'}/>}
        {refresh.isSuccess && <Alert type="success" title="行情已刷新；分时估值由盘中数据源独立更新"/>}
        <Tabs defaultActiveKey="intraday" items={[
            {key: 'intraday', label: '今日分时', children: <Card><FundIntradayChart portfolioFundId={portfolioFundId}/></Card>},
            {key: 'kline', label: 'K线 / 走势图', children: <Card><KlineChart portfolioFundId={portfolioFundId} fundSubType={fundSubType}/></Card>},
        ]}/>
        </>
    );
}
