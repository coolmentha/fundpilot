import IndexTicker from '../components/IndexTicker.jsx';
import PortfolioOverview from '../components/PortfolioOverview.jsx';
import FundWatchlist from '../components/FundWatchlist.jsx';
import SectorPerformance from '../components/SectorPerformance.jsx';
import MoneyFlow from '../components/MoneyFlow.jsx';
import {Link} from 'react-router-dom';
import {usePendingSignals, usePortfolioSummary} from '../api/hooks.js';

/**
 * 行情工作台(首页)。
 *
 * <p>三区结构:
 * <ol>
 *   <li>顶部:组合总览 + 大盘指数条</li>
 *   <li>中部:自选基金行情列表(可排序,10s 轮询估值)</li>
 *   <li>底部:行业板块涨跌 + 资金流向(双栏,30s 轮询)</li>
 * </ol>
 *
 * <p>交易时段(9:30-11:30, 13:00-15:00)后端定时刷新缓存,前端轮询只读内存。
 * 非交易时段数据不变,前端继续展示上一交易日收盘数据。
 */
export default function MarketDashboardPage() {
    const {data: summary} = usePortfolioSummary();
    const {data: pending} = usePendingSignals();
    const unknownCount = Math.max(
        (summary?.holdingFundCount ?? 0) - (summary?.risingFundCount ?? 0) - (summary?.fallingFundCount ?? 0), 0);

    return (
        <div className="market-dashboard">
            <section className="dashboard-section overview-section" aria-label="组合总览">
                <div className="section-header">
                    <h3 className="section-title">总览</h3>
                </div>
                <PortfolioOverview/>
            </section>

            <section className="dashboard-section index-section" aria-label="大盘指数">
                <IndexTicker/>
            </section>

            <section className="dashboard-section watchlist-section" aria-label="自选基金行情">
                <div className="section-header">
                    <h3 className="section-title">我的持仓</h3>
                </div>
                <FundWatchlist/>
            </section>

            <section className="dashboard-status-grid" aria-label="持仓状态与待处理">
                <div className="dashboard-status-item">
                    <span className="muted">持仓涨跌分布</span>
                    <div className="distribution-values">
                        <strong className="pnl-up">{summary?.risingFundCount ?? 0} 上涨</strong>
                        <strong className="pnl-down">{summary?.fallingFundCount ?? 0} 下跌</strong>
                        <strong className="muted">{unknownCount} 未知</strong>
                    </div>
                </div>
                <div className="dashboard-status-item">
                    <span className="muted">待处理</span>
                    <div><strong>{pending?.length ?? 0} 个操作待确认</strong> <Link to="/confirm">查看 →</Link></div>
                </div>
            </section>

            <section className="dashboard-section bottom-section" aria-label="板块与资金流向">
                <div className="bottom-grid">
                    <div className="bottom-card">
                        <div className="section-header">
                            <h3 className="section-title">行业板块涨跌</h3>
                        </div>
                        <SectorPerformance/>
                    </div>
                    <div className="bottom-card">
                        <div className="section-header">
                            <h3 className="section-title">主力资金流向</h3>
                        </div>
                        <MoneyFlow/>
                    </div>
                </div>
            </section>
        </div>
    );
}
