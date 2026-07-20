import {useState} from 'react';
import {Table} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
import {Link} from 'react-router-dom';
import {useFundGroups, useFunds, useFundEstimates} from '../api/hooks.js';
import {money, signedMoney, signedPercent, pnlColor, text} from '../constants.js';
import {buildFundWatchlistRows, estimateStatusText, selectHoldingRows} from '../querySafety.js';
import QueryErrorState from './QueryErrorState.jsx';
import FundGroupTabs from './FundGroupTabs.jsx';
import {ALL_GROUPS_KEY, filterFundsByGroup} from '../fundGroups.js';

/**
 * 自选基金行情列表:展示所有持仓/观察基金的实时涨跌(来自 fundgz 盘中估值)。
 *
 * <p>与 FundsPage 的差异:FundsPage 是档案管理(CRUD),本组件是行情看板
 * (实时涨跌排序、点击进详情)。涨跌数据来自 useFundEstimates(盘中估值缓存),
 * 失败时明确显示失败状态,不回退旧估值或上一交易日数据。
 *
 * <p>支持按涨跌幅排序(默认降序,涨幅大的在前)。
 */
export default function FundWatchlist() {
    const {data: funds, isLoading: fundsLoading, isError: fundsError, refetch: refetchFunds} = useFunds();
    const {data: fundGroups} = useFundGroups();
    const [activeGroup, setActiveGroup] = useState(ALL_GROUPS_KEY);
    const codes = (funds || []).map((f) => f.fundCode).filter(Boolean);
    const {
        data: estimates,
        isFetched: estimatesFetched,
        isError: estimatesError,
        refetch: refetchEstimates,
    } = useFundEstimates(codes);

    // 合并基金档案 + 实时估值。失败态优先,防止两个轮询接口刷新时序不同导致旧估值回退。
    const rows = buildFundWatchlistRows(funds, estimates, {estimatesFetched, estimatesError});

    const effectiveActiveGroup = activeGroup === ALL_GROUPS_KEY
        || (fundGroups || []).some((group) => String(group.id) === activeGroup) ? activeGroup : ALL_GROUPS_KEY;

    const columns = [
        {
            title: '基金',
            dataIndex: 'fundName',
            width: 260,
            ellipsis: true,
            render: (v, r) => (
                <span className="watchlist-name-cell">
                    <Link className="watchlist-name-text" title={v} to={`/funds/${r.id}`}>
                        <strong>{v}</strong><small>{r.fundCode} · {text(r.fundSubType)}</small>
                    </Link>
                </span>
            ),
        },
        {
            title: '持仓市值', dataIndex: 'holdingAmount', width: 130, align: 'right',
            render: (v) => v == null ? <span className="muted">-</span> : <span className="num-cell">{money(v)}</span>,
        },
        {
            title: '仓位', dataIndex: 'allocationPct', width: 80, align: 'right',
            render: (v) => v == null ? <span className="muted">-</span> : <span className="num-cell">{v.toFixed(1)}%</span>,
        },
        {
            title: '涨跌幅',
            dataIndex: 'changePct',
            width: 110,
            align: 'right',
            sorter: (a, b) => (a.changePct ?? -Infinity) - (b.changePct ?? -Infinity),
            defaultSortOrder: 'descend',
            render: (v, r) => {
                const statusText = estimateStatusText(r.estimateStatus);
                if (statusText) return <span className={r.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{statusText}</span>;
                if (v === null || v === undefined) return <span className="muted">-</span>;
                const color = pnlColor(v);
                const isUp = Number(v) > 0;
                const isDown = Number(v) < 0;
                return (
                    <span style={{color}}>
                        {isUp && <ArrowUpOutlined/>}
                        {isDown && <ArrowDownOutlined/>}
                        <span style={{marginLeft: 4}}>{signedPercent(v)}</span>
                        {r.isEstimated && <span className="estimate-tag" title={`估值时间 ${r.estimateTime || ''}`}>估</span>}
                    </span>
                );
            },
        },
        {
            title: '当日收益',
            dataIndex: 'dailyPnl',
            width: 120,
            align: 'right',
            responsive: ['sm'],
            render: (v, r) => estimateStatusText(r.estimateStatus)
                ? <span className={r.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{estimateStatusText(r.estimateStatus)}</span>
                : v !== null && v !== undefined
                    ? <span className="num-cell" style={{color: pnlColor(v)}}>{signedMoney(v)}</span>
                    : <span className="muted">-</span>,
        },
        {
            title: '数据状态', width: 110, align: 'right',
            render: (_, r) => <span className={r.estimateFetchFailed ? 'estimate-failure' : 'muted'}>
                {estimateStatusText(r.estimateStatus) || (r.isEstimated ? '盘中估值' : '净值已确认')}
            </span>,
        },
    ];

    if (fundsError) {
        return (
            <div className="fund-watchlist">
                <QueryErrorState onRetry={refetchFunds} description="基金列表加载失败"/>
            </div>
        );
    }

    const holdingRows = filterFundsByGroup(selectHoldingRows(rows), effectiveActiveGroup);
    const totalHoldingAmount = holdingRows.reduce((sum, r) => sum + Number(r.holdingAmount), 0);
    const displayRows = holdingRows.map((r) => ({
        ...r,
        allocationPct: totalHoldingAmount > 0
            ? Number(r.holdingAmount || 0) / totalHoldingAmount * 100 : null,
    }));

    return (
        <div className="fund-watchlist">
            {estimatesError && (
                <QueryErrorState onRetry={refetchEstimates} description="实时估值加载失败，已隐藏旧估值"/>
            )}
            <FundGroupTabs groups={fundGroups} activeKey={effectiveActiveGroup} onChange={setActiveGroup}/>
            <div className="watchlist-layout">
            <Table
                dataSource={displayRows}
                columns={columns}
                loading={fundsLoading}
                size="small"
                pagination={false}
                tableLayout="fixed"
                rowClassName={(r) => r.status === 'HOLDING' ? 'row-holding' : ''}
                locale={{emptyText: (
                    <span className="muted">
                        暂无基金,<Link to="/funds">去「我的基金」添加</Link>
                    </span>
                )}}
            />
            <aside className="allocation-panel" aria-label="仓位构成">
                <div className="allocation-title"><strong>仓位构成</strong><span>100%</span></div>
                {displayRows.filter((r) => r.allocationPct != null)
                    .sort((a, b) => b.allocationPct - a.allocationPct).map((r) => (
                    <div className="allocation-item" key={r.id}>
                        <div><span title={r.fundName}>{r.fundName}</span><strong>{r.allocationPct.toFixed(1)}%</strong></div>
                        <div className="allocation-bar"><i style={{width: `${r.allocationPct}%`}}/></div>
                    </div>
                ))}
            </aside>
            </div>
        </div>
    );
}
