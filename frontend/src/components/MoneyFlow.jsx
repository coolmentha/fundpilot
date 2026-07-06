import {Skeleton} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
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

    const ranked = (sectors || [])
        .filter((s) => s.mainforceNet !== null && s.mainforceNet !== undefined)
        .sort((a, b) => Math.abs(Number(b.mainforceNet) || 0) - Math.abs(Number(a.mainforceNet) || 0))
        .slice(0, 8);

    if (ranked.length === 0) {
        return <div className="money-flow empty"><span className="muted">暂无资金流向数据</span></div>;
    }

    return (
        <div className="money-flow" role="list" aria-label="行业主力资金流向">
            {ranked.map((s, idx) => {
                const net = Number(s.mainforceNet) || 0;
                const color = pnlColor(net);
                const isUp = net > 0;
                const isDown = net < 0;
                return (
                    <div className={`flow-row ${idx === 0 ? 'primary' : ''}`} key={s.sectorCode || s.sectorName}
                         role="listitem">
                        <div className="flow-label">{s.sectorName || '-'}</div>
                        <div className="flow-value" style={{color}}>
                            {isUp && <ArrowUpOutlined/>}
                            {isDown && <ArrowDownOutlined/>}
                            <span style={{marginLeft: 4}}>{signedCompactMoney(s.mainforceNet)}</span>
                        </div>
                    </div>
                );
            })}
            <div className="flow-hint muted">
                按主力净流入绝对值排序
            </div>
        </div>
    );
}
