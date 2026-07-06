import {Skeleton} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
import {useSectorPerformance} from '../api/hooks.js';
import {signedPercent, compactMoney, signedCompactMoney, pnlColor} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

/**
 * 行业板块涨跌排行:展示板块名称、涨跌幅(进度条)、成交额、主力净流入。
 * 数据 30 秒轮询(见 useSectorPerformance)。最多展示前 10 个板块。
 */
export default function SectorPerformance() {
    const {data: sectors, isLoading, isError, refetch} = useSectorPerformance();

    if (isLoading) {
        return (
            <div className="sector-list">
                {[1, 2, 3, 4, 5].map((i) => (
                    <Skeleton key={i} active paragraph={{rows: 0}} title={{width: '100%'}}/>
                ))}
            </div>
        );
    }

    if (isError) {
        return <div className="sector-list empty"><QueryErrorState onRetry={refetch} description="板块数据加载失败"/></div>;
    }

    const list = (sectors || []).slice(0, 10);
    if (list.length === 0) {
        return <div className="sector-list empty"><span className="muted">暂无板块数据</span></div>;
    }

    // 计算涨跌幅最大绝对值,用作进度条比例基准(避免单板块满格)
    const maxAbs = Math.max(...list.map((s) => Math.abs(Number(s.changePct) || 0)), 0.01);

    return (
        <div className="sector-list" role="list" aria-label="行业板块涨跌">
            {list.map((s) => {
                const pct = Number(s.changePct) || 0;
                const color = pnlColor(pct);
                const widthPct = Math.min(Math.abs(pct) / maxAbs * 100, 100);
                const isUp = pct > 0;
                const isDown = pct < 0;
                return (
                    <div className="sector-row" key={s.sectorName} role="listitem">
                        <div className="sector-name">{s.sectorName || '-'}</div>
                        <div className="sector-bar-wrap">
                            <div className={`sector-bar ${isUp ? 'up' : isDown ? 'down' : ''}`}
                                 style={{width: `${widthPct}%`, background: color}}/>
                        </div>
                        <div className="sector-pct" style={{color}}>
                            {isUp && <ArrowUpOutlined/>}
                            {isDown && <ArrowDownOutlined/>}
                            <span style={{marginLeft: 4}}>{signedPercent(s.changePct)}</span>
                        </div>
                        {s.mainforceNet !== null && s.mainforceNet !== undefined && (
                            <div className="sector-flow" style={{color: pnlColor(s.mainforceNet)}}>
                                {signedCompactMoney(s.mainforceNet)}
                            </div>
                        )}
                    </div>
                );
            })}
        </div>
    );
}
