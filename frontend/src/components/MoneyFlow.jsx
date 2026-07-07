import {Skeleton} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined, SwapOutlined} from '@ant-design/icons';
import {useSectorPerformance} from '../api/hooks.js';
import {signedCompactMoney, pnlColor} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

/**
 * 资金流向:复用行业板块数据,按主力净流入排序展示。
 * 左侧行业板块按涨跌幅排序,本组件提供资金视角,避免依赖已失效的北向实时净流入。
 */
export default function MoneyFlow() {
    const {data: sectors, isLoading, isError, refetch} = useSectorPerformance();

    if (isLoading) {
        return (
            <div className="money-flow">
                <Skeleton active paragraph={{rows: 4}} title={{width: 80}}/>
            </div>
        );
    }

    if (isError) {
        return <div className="money-flow empty"><QueryErrorState onRetry={refetch} description="资金流向加载失败"/></div>;
    }

    const withFlow = (sectors || [])
        .filter((s) => s.mainforceNet !== null && s.mainforceNet !== undefined)
        .map((s) => ({...s, net: Number(s.mainforceNet) || 0}))
        .filter((s) => s.net !== 0);
    const inflow = withFlow.filter((s) => s.net > 0)
        .sort((a, b) => b.net - a.net)
        .slice(0, 4);
    const outflow = withFlow.filter((s) => s.net < 0)
        .sort((a, b) => a.net - b.net)
        .slice(0, 4);
    const ranked = [...inflow, ...outflow];

    if (ranked.length === 0) {
        return <div className="money-flow empty"><span className="muted">暂无资金流向数据</span></div>;
    }

    const totalNet = withFlow.reduce((sum, s) => sum + s.net, 0);
    const maxAbs = Math.max(...ranked.map((s) => Math.abs(s.net)), 1);
    const leader = ranked[0];

    return (
        <div className="money-flow" aria-label="行业主力资金流向">
            <div className="flow-summary">
                <div>
                    <div className="flow-summary-label">板块主力净额</div>
                    <div className="flow-summary-value" style={{color: pnlColor(totalNet)}}>
                        <SwapOutlined/>
                        <span>{signedCompactMoney(totalNet)}</span>
                    </div>
                </div>
                <div className="flow-summary-meta">
                    <span>{inflow.length} 个流入</span>
                    <span>{outflow.length} 个流出</span>
                </div>
            </div>

            <div className="flow-leader">
                <span className="flow-leader-label">资金焦点</span>
                <span className="flow-leader-name">{leader?.sectorName || '-'}</span>
                <span className="flow-leader-value" style={{color: pnlColor(leader?.net)}}>
                    {signedCompactMoney(leader?.mainforceNet)}
                </span>
            </div>

            <div className="flow-columns">
                <FlowColumn title="净流入" rows={inflow} maxAbs={maxAbs} tone="in"/>
                <FlowColumn title="净流出" rows={outflow} maxAbs={maxAbs} tone="out"/>
            </div>
        </div>
    );
}

function FlowColumn({title, rows, maxAbs, tone}) {
    return (
        <div className="flow-column" role="list" aria-label={title}>
            <div className="flow-column-title">{title}</div>
            {rows.length === 0 ? (
                <div className="flow-row empty-row">-</div>
            ) : rows.map((s) => {
                const widthPct = Math.max(Math.abs(s.net) / maxAbs * 100, 8);
                const isUp = s.net > 0;
                const isDown = s.net < 0;
                return (
                    <div className="flow-row" key={s.sectorCode || s.sectorName} role="listitem">
                        <div className="flow-row-top">
                            <span className="flow-label">{s.sectorName || '-'}</span>
                            <span className="flow-value" style={{color: pnlColor(s.net)}}>
                                {isUp && <ArrowUpOutlined/>}
                                {isDown && <ArrowDownOutlined/>}
                                <span>{signedCompactMoney(s.mainforceNet)}</span>
                            </span>
                        </div>
                        <div className={`flow-meter ${tone}`}>
                            <span style={{width: `${widthPct}%`}}/>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
