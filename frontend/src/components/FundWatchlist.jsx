import {Table, Tag} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
import {Link, useNavigate} from 'react-router-dom';
import {useFunds, useFundEstimates} from '../api/hooks.js';
import {signedMoney, signedPercent, compactMoney, pnlColor, text} from '../constants.js';
import {buildFundWatchlistRows} from '../querySafety.js';
import QueryErrorState from './QueryErrorState.jsx';

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
    const navigate = useNavigate();
    const {data: funds, isLoading: fundsLoading, isError: fundsError, refetch: refetchFunds} = useFunds();
    const codes = (funds || []).map((f) => f.fundCode).filter(Boolean);
    const {
        data: estimates,
        isFetched: estimatesFetched,
        isError: estimatesError,
        refetch: refetchEstimates,
    } = useFundEstimates(codes);

    // 合并基金档案 + 实时估值。失败态优先,防止两个轮询接口刷新时序不同导致旧估值回退。
    const rows = buildFundWatchlistRows(funds, estimates, {estimatesFetched, estimatesError});

    const columns = [
        {
            title: '代码',
            dataIndex: 'fundCode',
            width: 112,
            render: (v) => <span className="num-cell">{v}</span>,
        },
        {
            title: '名称',
            dataIndex: 'fundName',
            ellipsis: true,
            render: (v) => (
                <span className="watchlist-name-cell">
                    <span className="watchlist-name-text" title={v}>{v}</span>
                </span>
            ),
        },
        {
            title: '类型',
            dataIndex: 'fundSubType',
            width: 104,
            responsive: ['md'],
            render: (v) => v ? <Tag>{text(v)}</Tag> : <span className="muted">-</span>,
        },
        {
            title: '涨跌幅',
            dataIndex: 'changePct',
            width: 140,
            sorter: (a, b) => (a.changePct ?? -Infinity) - (b.changePct ?? -Infinity),
            defaultSortOrder: 'descend',
            render: (v, r) => {
                if (r.estimateFetchFailed) return <span className="estimate-failure">估值拉取失败</span>;
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
            title: '持仓份额',
            dataIndex: 'holdingShares',
            width: 128,
            align: 'right',
            responsive: ['sm'],
            render: (v) => v !== null && v !== undefined
                ? <span className="num-cell">{compactMoney(v)}</span>
                : <span className="muted">-</span>,
        },
        {
            title: '当日收益',
            dataIndex: 'dailyPnl',
            width: 132,
            align: 'right',
            responsive: ['sm'],
            render: (v, r) => r.estimateFetchFailed
                ? <span className="estimate-failure">估值拉取失败</span>
                : v !== null && v !== undefined
                    ? <span className="num-cell" style={{color: pnlColor(v)}}>{signedMoney(v)}</span>
                    : <span className="muted">-</span>,
        },
        {
            title: '操作',
            width: 88,
            render: (_, r) => (
                <a onClick={() => navigate(`/funds/${r.id}`)}>详情</a>
            ),
        },
    ];

    if (fundsError) {
        return (
            <div className="fund-watchlist">
                <QueryErrorState onRetry={refetchFunds} description="基金列表加载失败"/>
            </div>
        );
    }

    return (
        <div className="fund-watchlist">
            {estimatesError && (
                <QueryErrorState onRetry={refetchEstimates} description="实时估值加载失败，已隐藏旧估值"/>
            )}
            <Table
                dataSource={rows}
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
        </div>
    );
}
