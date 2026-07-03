import {Skeleton} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
import {useRealtimeIndices} from '../api/hooks.js';
import {signedPercent, pnlColor} from '../constants.js';

/**
 * 大盘指数条:横向展示用户关注的指数实时行情。
 * 每张卡片:名称、点位、涨跌幅(带↑↓箭头,A 股惯例红涨绿跌)。
 * 数据 5 秒轮询(见 useRealtimeIndices)。
 */
export default function IndexTicker() {
    const {data: indices, isLoading} = useRealtimeIndices();

    if (isLoading) {
        return (
            <div className="index-ticker">
                {[1, 2, 3].map((i) => (
                    <div className="index-card skeleton" key={i}>
                        <Skeleton active paragraph={{rows: 1, width: 80}} title={{width: 60}}/>
                    </div>
                ))}
            </div>
        );
    }

    if (!indices || indices.length === 0) {
        return (
            <div className="index-ticker empty">
                <span className="muted">暂无关注指数,请在「用户配置」添加</span>
            </div>
        );
    }

    return (
        <div className="index-ticker" role="list" aria-label="大盘指数行情">
            {indices.map((idx) => {
                const color = pnlColor(idx.changePct);
                const isUp = Number(idx.changePct) > 0;
                const isDown = Number(idx.changePct) < 0;
                return (
                    <div className="index-card" key={idx.secid || idx.name} role="listitem"
                         style={color ? {'--card-accent': color} : undefined}>
                        <div className="index-name">{idx.name || '-'}</div>
                        <div className="index-price">{formatPrice(idx.currentPrice)}</div>
                        <div className="index-change" style={{color}}>
                            {isUp && <ArrowUpOutlined/>}
                            {isDown && <ArrowDownOutlined/>}
                            <span className="index-pct">{signedPercent(idx.changePct)}</span>
                            {idx.changeAmount !== null && idx.changeAmount !== undefined && (
                                <span className="index-amount">{formatPrice(idx.changeAmount)}</span>
                            )}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

// 点位/涨跌额格式化:保留 2 位小数,千分位。
function formatPrice(value) {
    if (value === null || value === undefined) return '-';
    return Number(value).toLocaleString('zh-CN', {maximumFractionDigits: 2, minimumFractionDigits: 2});
}
