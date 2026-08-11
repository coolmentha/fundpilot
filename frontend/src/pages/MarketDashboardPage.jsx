import {Link} from 'react-router-dom';
import IndexTicker from '../components/IndexTicker.jsx';
import PortfolioOverview from '../components/PortfolioOverview.jsx';
import FundWatchlist from '../components/FundWatchlist.jsx';
import SectorPerformance from '../components/SectorPerformance.jsx';
import {useFunds, useMarketStatus} from '../api/hooks.js';
import {date, datetime, pnlColor, signedMoney} from '../constants.js';
import {selectContributors} from '../querySafety.js';

const MARKET_STATE_LABELS = {
    PRE_OPEN: 'A 股盘前',
    TRADING: 'A 股交易中',
    LUNCH_BREAK: 'A 股午间休市',
    CLOSED: 'A 股已收盘',
    NON_TRADING_DAY: 'A 股休市',
};

/** 行情工作台：首页聚合市场状态、组合表现、持仓贡献和行业表现。 */
export default function MarketDashboardPage() {
    const {data: marketStatus} = useMarketStatus();
    const {data: funds} = useFunds();
    const {contributor, detractor} = selectContributors(funds);
    const updatedAt = datetime(marketStatus?.updatedAt);
    const updatedAtLabel = updatedAt === '-' ? '数据时间待刷新'
        : `数据截至 ${date(marketStatus.updatedAt) === date(new Date().toISOString()) ? updatedAt.slice(-8) : updatedAt}`;

    return (
        <div className="market-dashboard">
            <section className="dashboard-section overview-section" aria-label="组合总览">
                <div className="section-header">
                    <h3 className="section-title">总览</h3>
                    <div className="market-status" role="status">
                        <strong>{MARKET_STATE_LABELS[marketStatus?.marketState] || 'A 股状态待更新'}</strong>
                        <span>{updatedAtLabel}</span>
                    </div>
                </div>
                <PortfolioOverview/>
            </section>

            <section className="dashboard-section index-section" aria-label="大盘指数">
                <IndexTicker/>
            </section>

            <section className="dashboard-section watchlist-section" aria-label="持仓表现">
                <div className="section-header"><h3 className="section-title">我的持仓</h3></div>
                <FundWatchlist/>
            </section>

            <section className="dashboard-status-grid" aria-label="今日持仓贡献">
                <ContributionItem title="今日最大贡献" fund={contributor}/>
                <ContributionItem title="今日最大拖累" fund={detractor}/>
            </section>

            <section className="dashboard-section bottom-section" aria-label="行业表现">
                <div className="section-header"><h3 className="section-title">行业表现</h3></div>
                <SectorPerformance/>
            </section>
        </div>
    );
}

function ContributionItem({title, fund}) {
    return (
        <div className="dashboard-status-item contribution-item">
            <span className="muted">{title}</span>
            {fund ? (
                <div>
                    <Link to={`/funds/${fund.id}`}>{fund.fundName}</Link>
                    <strong style={{color: pnlColor(fund.dailyPnl)}}>{signedMoney(fund.dailyPnl)}</strong>
                </div>
            ) : <strong className="muted">-</strong>}
        </div>
    );
}
