import {useMemo, useState} from 'react';
import {Segmented, Skeleton} from 'antd';
import {useSectorPerformance} from '../api/hooks.js';
import QueryErrorState from './QueryErrorState.jsx';
import MoneyFlow from './MoneyFlow.jsx';
import {filterSectors, sectorFlowDirection, sortSectors} from '../querySafety.js';

const SORT_OPTIONS = [
    {label: '涨跌幅', value: 'changePct'},
    {label: '成交额', value: 'turnover'},
    {label: '主力净额', value: 'mainforceNet'},
    {label: '主力净占比', value: 'mainforceRatio'},
];

const DIRECTION_OPTIONS = [
    {label: '全部', value: 'all'},
    {label: '净流入', value: 'inflow'},
    {label: '净流出', value: 'outflow'},
];

const EMPTY_TEXT = {
    inflow: '暂无主力净流入行业',
    outflow: '暂无主力净流出行业',
};

/** 全市场行业表现。后端翻页取全约 500 个行业，排序与资金方向筛选在前端基于该完整范围进行。 */
export default function SectorPerformance() {
    const {data: sectors, isLoading, isError, refetch} = useSectorPerformance();
    const [sortBy, setSortBy] = useState('changePct');
    const [direction, setDirection] = useState('all');

    // 方向选项带全量条数，数据刷新时同步更新；计数始终基于未筛选的行业范围。
    const directionOptions = useMemo(() => {
        const counts = {inflow: 0, outflow: 0};
        (sectors || []).forEach((sector) => {
            const flow = sectorFlowDirection(sector);
            if (flow) counts[flow] += 1;
        });
        return DIRECTION_OPTIONS.map((option) => option.value === 'all'
            ? option
            : {...option, label: `${option.label} ${counts[option.value]}`});
    }, [sectors]);

    // 净流出视角按净额排序时取升序,让流出最多的行业排在最前(降序会把它们压到末页)。
    const ascending = direction === 'outflow' && sortBy === 'mainforceNet';
    const sortOptions = useMemo(() => SORT_OPTIONS.map((option) => (
        option.value === 'mainforceNet' && ascending ? {...option, label: '净流出额'} : option
    )), [ascending]);
    const rows = useMemo(
        () => sortSectors(filterSectors(sectors, direction), sortBy, ascending),
        [sectors, direction, sortBy, ascending]);

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
                <Segmented options={directionOptions} value={direction} onChange={setDirection}/>
                <Segmented options={sortOptions} value={sortBy} onChange={setSortBy}/>
            </div>
            <MoneyFlow key={`${direction}-${sortBy}`} sectors={rows} emptyText={EMPTY_TEXT[direction]}/>
        </div>
    );
}
