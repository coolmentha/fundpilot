import {Skeleton} from 'antd';
import {LineChartOutlined} from '@ant-design/icons';
import {useMarketVolumePrice} from '../api/hooks.js';
import {datetime, pnlColor, signedPercent} from '../constants.js';

const STATE_META = {
    HIGH_UP: {
        label: '放量上涨', tone: 'up',
        advice: '趋势获得量能确认，按计划持有或观察，避免急涨追高。',
    },
    LOW_UP: {
        label: '缩量上涨', tone: 'up',
        advice: '上行动能不足，不追高，等待量能确认。',
    },
    HIGH_DOWN: {
        label: '放量下跌', tone: 'down',
        advice: '抛压扩大，优先控制风险，避免盲目抄底并检查风险敞口。',
    },
    LOW_DOWN: {
        label: '缩量下跌', tone: 'down',
        advice: '抛压有限但方向偏弱，观察支撑，不做恐慌操作。',
    },
    NORMAL_UP: {
        label: '量能平稳上涨', tone: 'up',
        advice: '市场温和偏强，继续按既定计划执行并观察持续性。',
    },
    NORMAL_DOWN: {
        label: '量能平稳下跌', tone: 'down',
        advice: '市场温和偏弱，避免扩大风险敞口并关注下行变化。',
    },
    FLAT: {
        label: '量价平稳', tone: 'neutral',
        advice: '方向尚未形成，等待价格与量能给出一致信号。',
    },
    UNAVAILABLE: {
        label: '量能观察中', tone: 'unavailable', advice: null,
    },
};

export default function MarketVolumePrice() {
    const {data, isLoading, isError} = useMarketVolumePrice();

    if (isLoading && !data) {
        return (
            <div className="market-volume-price loading" role="status" aria-label="市场量价加载中">
                <Skeleton active paragraph={{rows: 1}} title={{width: 120}}/>
            </div>
        );
    }

    const state = isError ? 'UNAVAILABLE' : data?.state;
    const stateMeta = STATE_META[state] || STATE_META.UNAVAILABLE;
    const available = stateMeta !== STATE_META.UNAVAILABLE
        && isFiniteNumber(data?.changePct) && isFiniteNumber(data?.volumeRatio);
    const meta = available ? stateMeta : STATE_META.UNAVAILABLE;
    const phase = data?.phase === 'INTRADAY_ESTIMATE' ? '盘中暂估' : '收盘';
    const quoteTime = datetime(data?.quoteTime);

    return (
        <div className={`market-volume-price ${meta.tone}`} role="status" aria-live="polite">
            <div className="volume-price-state">
                <LineChartOutlined aria-hidden="true"/>
                <div>
                    <span className="volume-price-label">市场量价</span>
                    <strong>{meta.label}</strong>
                </div>
                <span className="volume-price-phase">{phase}</span>
            </div>
            <div className="volume-price-metrics" aria-label={available
                ? `上证涨跌幅 ${signedPercent(data.changePct)}，量比 ${formatRatio(data.volumeRatio)}`
                : '上证量价数据暂不可用'}>
                <span>上证 <strong style={{color: pnlColor(data?.changePct)}}>
                    {available ? signedPercent(data.changePct) : '-'}
                </strong></span>
                <span>量比 <strong>{available ? formatRatio(data.volumeRatio) : '-'}</strong></span>
            </div>
            <div className="volume-price-advice">
                <strong>{meta.advice || '实时量价数据暂不可用，暂不生成纪律提醒。'}</strong>
                <span>{quoteTime === '-' ? '行情时间待刷新' : `行情截至 ${quoteTime}`}</span>
            </div>
        </div>
    );
}

function formatRatio(value) {
    return isFiniteNumber(value) ? Number(value).toFixed(2) : '-';
}

function isFiniteNumber(value) {
    return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value));
}
