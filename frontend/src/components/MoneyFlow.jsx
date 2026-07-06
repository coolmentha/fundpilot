import {Skeleton} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
import {useMoneyFlow} from '../api/hooks.js';
import {signedCompactMoney, pnlColor, datetime} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

/**
 * 资金流向:展示北向资金净流入。本期只含北向一项(全市场主力汇总接口不稳定)。
 * 板块级主力资金在 SectorPerformance 组件中随板块展示。
 * 数据 30 秒轮询(见 useMoneyFlow)。
 */
export default function MoneyFlow() {
    const {data: flow, isLoading, isError, refetch} = useMoneyFlow();

    if (isLoading) {
        return (
            <div className="money-flow">
                <Skeleton active paragraph={{rows: 1}} title={{width: 80}}/>
            </div>
        );
    }

    if (isError) {
        return <div className="money-flow empty"><QueryErrorState onRetry={refetch} description="资金流向加载失败"/></div>;
    }

    if (!flow) {
        return <div className="money-flow empty"><span className="muted">暂无资金流向数据</span></div>;
    }

    const net = Number(flow.northboundNet);
    const color = pnlColor(net);
    const isUp = net > 0;
    const isDown = net < 0;

    return (
        <div className="money-flow" role="region" aria-label="资金流向">
            <div className="flow-row primary">
                <div className="flow-label">北向资金净流入</div>
                <div className="flow-value" style={{color}}>
                    {isUp && <ArrowUpOutlined/>}
                    {isDown && <ArrowDownOutlined/>}
                    <span style={{marginLeft: 4}}>{signedCompactMoney(flow.northboundNet)}</span>
                </div>
            </div>
            {flow.snapshotTime && (
                <div className="flow-time muted">数据时间: {datetime(flow.snapshotTime)}</div>
            )}
            <div className="flow-hint muted">
                板块级主力资金见左侧「行业板块涨跌」
            </div>
        </div>
    );
}
