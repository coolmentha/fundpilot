import {useState} from 'react';
import {Table} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
import {Link} from 'react-router-dom';
import {useFundGroups, useFunds, useFundEstimates} from '../api/hooks.js';
import {date, datetime, money, signedMoney, signedPercent, pnlColor, text} from '../constants.js';
import {buildFundWatchlistRows, estimateStatusText, selectHoldingRows} from '../querySafety.js';
import QueryErrorState from './QueryErrorState.jsx';
import FundGroupTabs from './FundGroupTabs.jsx';
import {ALL_GROUPS_KEY, filterFundsByGroup, getStoredFundGroup, storeFundGroup} from '../fundGroups.js';
import {valuationStatusText} from './valuationStatusText.js';

/** 行情工作台持仓表现，默认按今日盈亏贡献降序。 */
export default function FundWatchlist() {
    const {data: funds, isLoading: fundsLoading, isError: fundsError, refetch: refetchFunds} = useFunds();
    const {data: fundGroups} = useFundGroups();
    const [activeGroup, setActiveGroup] = useState(getStoredFundGroup);
    const codes = (funds || []).map((fund) => fund.fundCode).filter(Boolean);
    const {
        data: estimates,
        isFetched: estimatesFetched,
        isError: estimatesError,
        refetch: refetchEstimates,
    } = useFundEstimates(codes);

    const rows = buildFundWatchlistRows(funds, estimates, {estimatesFetched, estimatesError});
    const effectiveActiveGroup = activeGroup === ALL_GROUPS_KEY
        || (fundGroups || []).some((group) => String(group.id) === activeGroup) ? activeGroup : ALL_GROUPS_KEY;

    const columns = [
        {
            title: '基金',
            dataIndex: 'fundName',
            width: 240,
            ellipsis: true,
            render: (value, row) => (
                <span className="watchlist-name-cell">
                    <Link className="watchlist-name-text" title={value} to={`/funds/${row.id}`}>
                        <strong>{value}</strong><small>{row.fundCode} · {text(row.fundSubType)}</small>
                    </Link>
                </span>
            ),
        },
        {
            title: '持仓市值', dataIndex: 'holdingAmount', width: 130, align: 'right',
            render: (value) => value == null
                ? <span className="muted">-</span>
                : <span className="num-cell">{money(value)}</span>,
        },
        {
            title: '累计收益率', dataIndex: 'returnRate', width: 120, align: 'right',
            sorter: (a, b) => numericSort(a.returnRate, b.returnRate),
            render: (value) => (
                <span className="num-cell" style={{color: pnlColor(value)}}>{signedPercent(value)}</span>
            ),
        },
        {
            title: '今日涨跌',
            dataIndex: 'changePct',
            width: 120,
            align: 'right',
            sorter: (a, b) => numericSort(a.changePct, b.changePct),
            render: (value, row) => {
                const status = displayEstimateStatus(row);
                if (status) return <span className={row.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{status}</span>;
                if (value == null) return <span className="muted">-</span>;
                return (
                    <span style={{color: pnlColor(value)}}>
                        {Number(value) > 0 && <ArrowUpOutlined/>}
                        {Number(value) < 0 && <ArrowDownOutlined/>}
                        <span style={{marginLeft: 4}}>{signedPercent(value)}</span>
                    </span>
                );
            },
        },
        {
            title: '今日盈亏',
            dataIndex: 'dailyPnl',
            width: 130,
            align: 'right',
            sorter: (a, b) => numericSort(a.dailyPnl, b.dailyPnl),
            defaultSortOrder: 'descend',
            render: (value, row) => displayEstimateStatus(row)
                ? <span className={row.estimateFetchFailed ? 'estimate-failure' : 'muted'}>
                    {displayEstimateStatus(row)}
                </span>
                : value == null
                    ? <span className="muted">-</span>
                    : <span className="num-cell" style={{color: pnlColor(value)}}>{signedMoney(value)}</span>,
        },
        {
            title: '估值 / 确认净值', width: 250,
            render: (_, row) => <ValuationCell row={row}/>,
        },
    ];

    if (fundsError) {
        return <div className="fund-watchlist"><QueryErrorState onRetry={refetchFunds} description="基金列表加载失败"/></div>;
    }

    const displayRows = filterFundsByGroup(selectHoldingRows(rows), effectiveActiveGroup)
        .sort((a, b) => numericSort(b.dailyPnl, a.dailyPnl));

    return (
        <div className="fund-watchlist">
            {estimatesError && (
                <QueryErrorState onRetry={refetchEstimates} description="实时估值加载失败，已隐藏旧估值"/>
            )}
            <FundGroupTabs groups={fundGroups} activeKey={effectiveActiveGroup} onChange={(groupKey) => {
                setActiveGroup(groupKey);
                storeFundGroup(groupKey);
            }}/>
            <Table
                dataSource={displayRows}
                columns={columns}
                loading={fundsLoading}
                size="small"
                pagination={false}
                tableLayout="fixed"
                rowClassName={(row) => row.status === 'HOLDING' ? 'row-holding' : ''}
                locale={{emptyText: (
                    <span className="muted">暂无基金，<Link to="/funds">去「我的基金」添加</Link></span>
                )}}
            />
        </div>
    );
}

function ValuationCell({row}) {
    const status = estimateStatusText(row.estimateStatus);
    if (status && row.valuationNav == null) {
        return <span className={row.estimateFetchFailed ? 'estimate-failure' : 'muted'}>{status}</span>;
    }
    const nav = row.valuationNav == null ? '-' : Number(row.valuationNav).toFixed(4);
    if (row.isEstimated) {
        return <span className="valuation-cell"><strong>盘中估值 {nav}</strong><small>{row.estimateTime || '-'}</small></span>;
    }
    const observedAt = row.valuationFirstSeenAt
        ? `平台发现 ${datetime(row.valuationFirstSeenAt)}`
        : date(row.valuationDate);
    return (
        <span className="valuation-cell">
            <strong>{valuationStatusText(row)} · {nav}</strong>
            <small>{observedAt}</small>
        </span>
    );
}

function displayEstimateStatus(row) {
    return row.investmentTarget === 'QDII' && row.valuationSource === 'LATEST_CONFIRMED_NAV'
        ? null : estimateStatusText(row.estimateStatus);
}

function numericSort(left, right) {
    const a = left == null || !Number.isFinite(Number(left)) ? -Infinity : Number(left);
    const b = right == null || !Number.isFinite(Number(right)) ? -Infinity : Number(right);
    return a - b;
}
