import IndexTicker from '../components/IndexTicker.jsx';
import FundWatchlist from '../components/FundWatchlist.jsx';
import SectorPerformance from '../components/SectorPerformance.jsx';
import MoneyFlow from '../components/MoneyFlow.jsx';

/**
 * 行情工作台(首页)。
 *
 * <p>三区结构:
 * <ol>
 *   <li>顶部:大盘指数条(横向滚动卡片,5s 轮询)</li>
 *   <li>中部:自选基金行情列表(可排序,10s 轮询估值)</li>
 *   <li>底部:行业板块涨跌 + 资金流向(双栏,30s 轮询)</li>
 * </ol>
 *
 * <p>交易时段(9:30-11:30, 13:00-15:00)后端定时刷新缓存,前端轮询只读内存。
 * 非交易时段数据不变,前端继续展示上一交易日收盘数据。
 */
export default function MarketDashboardPage() {
    return (
        <div className="market-dashboard">
            <section className="dashboard-section index-section" aria-label="大盘指数">
                <IndexTicker/>
            </section>

            <section className="dashboard-section watchlist-section" aria-label="自选基金行情">
                <div className="section-header">
                    <h3 className="section-title">自选基金</h3>
                    <span className="section-hint muted">点击表头排序,点击「详情」查看 K 线</span>
                </div>
                <FundWatchlist/>
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
                            <h3 className="section-title">资金流向</h3>
                        </div>
                        <MoneyFlow/>
                    </div>
                </div>
            </section>
        </div>
    );
}
