import {Skeleton} from 'antd';
import {ArrowDownOutlined, ArrowUpOutlined, BarChartOutlined, WalletOutlined} from '@ant-design/icons';
import {useMarketBreadth, usePortfolioSummary} from '../api/hooks.js';
import {pnlColor, signedMoney} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

/**
 * 行情工作台总览:展示全仓今日收益、基金涨跌数量与沪深京市场宽度。
 */
export default function PortfolioOverview() {
    const {data: summary, isLoading, isError, refetch} = usePortfolioSummary();
    const {data: breadth, isError: isBreadthError} = useMarketBreadth();

    if (isLoading && !summary) {
        return (
            <div className="portfolio-overview-grid">
                {[1, 2, 3, 4].map((i) => (
                    <div className="portfolio-overview-card" key={i}>
                        <Skeleton active paragraph={{rows: 1, width: 96}} title={{width: 80}}/>
                    </div>
                ))}
            </div>
        );
    }

    if (isError) {
        return (
            <div className="portfolio-overview-error">
                <QueryErrorState onRetry={refetch} description="组合总览加载失败"/>
            </div>
        );
    }

    const dailyPnlTotal = summary?.dailyPnlTotal;
    const estimateFetchFailedCount = summary?.estimateFetchFailedCount ?? 0;
    const estimateFetchFailed = estimateFetchFailedCount > 0;
    const risingFundCount = summary?.risingFundCount ?? 0;
    const fallingFundCount = summary?.fallingFundCount ?? 0;
    const risingStockCount = breadth?.risingCount;
    const fallingStockCount = breadth?.fallingCount;
    const hasBreadth = Number.isFinite(risingStockCount)
        && Number.isFinite(fallingStockCount)
        && risingStockCount >= 0
        && fallingStockCount >= 0;
    const breadthTotal = hasBreadth ? risingStockCount + fallingStockCount : 0;
    const risingPercent = breadthTotal > 0 ? (risingStockCount / breadthTotal) * 100 : 0;
    const fallingPercent = breadthTotal > 0 ? 100 - risingPercent : 0;
    const breadthAriaLabel = breadthTotal > 0
        ? `沪深京股票上涨 ${risingStockCount} 只，占 ${risingPercent.toFixed(1)}%；下跌 ${fallingStockCount} 只，占 ${fallingPercent.toFixed(1)}%`
        : '沪深京股票涨跌数据暂不可用';

    return (
        <div className="portfolio-overview-grid" role="list" aria-label="组合收益与市场宽度总览">
            <div className="portfolio-overview-card primary" role="listitem">
                <div className="overview-label">
                    <WalletOutlined/>
                    <span>全仓收益</span>
                </div>
                <div className={`overview-value${estimateFetchFailed ? ' failure' : ''}`}
                     style={estimateFetchFailed ? undefined : {color: pnlColor(dailyPnlTotal)}}>
                    {estimateFetchFailed ? '估值拉取失败' : signedMoney(dailyPnlTotal)}
                </div>
                <div className="overview-hint muted">
                    {estimateFetchFailed ? `${estimateFetchFailedCount} 只持仓基金` : '今日合计'}
                </div>
            </div>
            <div className="portfolio-overview-card up" role="listitem">
                <div className="overview-label">
                    <ArrowUpOutlined/>
                    <span>上涨基金</span>
                </div>
                <div className="overview-value">{risingFundCount}</div>
                <div className="overview-hint muted">只</div>
            </div>
            <div className="portfolio-overview-card down" role="listitem">
                <div className="overview-label">
                    <ArrowDownOutlined/>
                    <span>下跌基金</span>
                </div>
                <div className="overview-value">{fallingFundCount}</div>
                <div className="overview-hint muted">只</div>
            </div>
            <div className="portfolio-overview-card breadth" role="listitem">
                <div className="overview-label">
                    <BarChartOutlined/>
                    <span>大盘涨跌</span>
                </div>
                <div className="market-breadth-summary">
                    <span className="market-breadth-stat up">
                        上涨 <strong>{hasBreadth ? risingStockCount : '-'}</strong>
                        {breadthTotal > 0 && <small>{risingPercent.toFixed(1)}%</small>}
                    </span>
                    <span className="market-breadth-stat down">
                        下跌 <strong>{hasBreadth ? fallingStockCount : '-'}</strong>
                        {breadthTotal > 0 && <small>{fallingPercent.toFixed(1)}%</small>}
                    </span>
                </div>
                <div className="market-breadth-bar" role="img" aria-label={breadthAriaLabel}>
                    {breadthTotal > 0 && (
                        <>
                            <span className="market-breadth-up" style={{width: `${risingPercent}%`}}/>
                            <span className="market-breadth-down" style={{width: `${fallingPercent}%`}}/>
                        </>
                    )}
                </div>
                <div className="overview-hint muted">
                    {isBreadthError && !breadth ? '行情暂不可用' : '沪深京股票'}
                </div>
            </div>
        </div>
    );
}
