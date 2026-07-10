import {Skeleton} from 'antd';
import {ArrowDownOutlined, ArrowUpOutlined, WalletOutlined} from '@ant-design/icons';
import {usePortfolioSummary} from '../api/hooks.js';
import {pnlColor, signedMoney} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

/**
 * 行情工作台总览:展示全仓今日收益与上涨/下跌基金数量。
 */
export default function PortfolioOverview() {
    const {data: summary, isLoading, isError, refetch} = usePortfolioSummary();

    if (isLoading && !summary) {
        return (
            <div className="portfolio-overview-grid">
                {[1, 2, 3].map((i) => (
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
    const risingFundCount = summary?.risingFundCount ?? 0;
    const fallingFundCount = summary?.fallingFundCount ?? 0;

    return (
        <div className="portfolio-overview-grid" role="list" aria-label="组合收益总览">
            <div className="portfolio-overview-card primary" role="listitem">
                <div className="overview-label">
                    <WalletOutlined/>
                    <span>全仓收益</span>
                </div>
                <div className="overview-value" style={{color: pnlColor(dailyPnlTotal)}}>
                    {signedMoney(dailyPnlTotal)}
                </div>
                <div className="overview-hint muted">今日合计</div>
            </div>
            <div className="portfolio-overview-card up" role="listitem">
                <div className="overview-label">
                    <ArrowUpOutlined/>
                    <span>今日上涨</span>
                </div>
                <div className="overview-value">{risingFundCount}</div>
                <div className="overview-hint muted">只</div>
            </div>
            <div className="portfolio-overview-card down" role="listitem">
                <div className="overview-label">
                    <ArrowDownOutlined/>
                    <span>今日下跌</span>
                </div>
                <div className="overview-value">{fallingFundCount}</div>
                <div className="overview-hint muted">只</div>
            </div>
        </div>
    );
}
