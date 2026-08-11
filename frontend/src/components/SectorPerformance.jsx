import {useState} from 'react';
import {Segmented, Skeleton} from 'antd';
import {useSectorPerformance} from '../api/hooks.js';
import QueryErrorState from './QueryErrorState.jsx';
import MoneyFlow from './MoneyFlow.jsx';
import {sortSectors} from '../querySafety.js';

const SORT_OPTIONS = [
    {label: '涨跌幅', value: 'changePct'},
    {label: '成交额', value: 'turnover'},
    {label: '主力净占比', value: 'mainforceRatio'},
];

/** 全市场行业表现，排序基于后端返回的完整行业范围。 */
export default function SectorPerformance() {
    const {data: sectors, isLoading, isError, refetch} = useSectorPerformance();
    const [sortBy, setSortBy] = useState('changePct');

    if (isLoading) {
        return <div className="industry-performance"><Skeleton active paragraph={{rows: 6}}/></div>;
    }
    if (isError) {
        return <div className="industry-performance empty">
            <QueryErrorState onRetry={refetch} description="行业数据加载失败"/>
        </div>;
    }
    if (!sectors?.length) {
        return <div className="industry-performance empty"><span className="muted">暂无行业数据</span></div>;
    }

    return (
        <div className="industry-performance">
            <div className="industry-toolbar">
                <Segmented options={SORT_OPTIONS} value={sortBy} onChange={setSortBy}/>
            </div>
            <MoneyFlow sectors={sortSectors(sectors, sortBy)}/>
        </div>
    );
}
