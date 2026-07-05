import {Table, Tag} from 'antd';
import {ArrowUpOutlined, ArrowDownOutlined} from '@ant-design/icons';
import {Link, useNavigate} from 'react-router-dom';
import {useFunds, useFundEstimates} from '../api/hooks.js';
import {signedPercent, compactMoney, pnlColor, text} from '../constants.js';
import QueryErrorState from './QueryErrorState.jsx';

/**
 * 自选基金行情列表:展示所有持仓/观察基金的实时涨跌(来自 fundgz 盘中估值)。
 *
 * <p>与 FundsPage 的差异:FundsPage 是档案管理(CRUD),本组件是行情看板
 * (实时涨跌排序、点击进详情)。涨跌数据来自 useFundEstimates(盘中估值缓存),
 * 失败时降级显示后端 fund.dailyChangePct(已结算净值涨跌)。
 *
 * <p>支持按涨跌幅排序(默认降序,涨幅大的在前)。
 */
export default function FundWatchlist() {
    const navigate = useNavigate();
    const {data: funds, isLoading: fundsLoading, isError: fundsError, refetch: refetchFunds} = useFunds();
    const codes = (funds || []).map((f) => f.fundCode).filter(Boolean);
    const {data: estimates} = useFundEstimates(codes);

    // 合并基金档案 + 实时估值。涨跌幅优先取估值(盘中),降级取 dailyChangePct(盘后/失败)。
    const rows = (funds || []).map((f) => {
        const est = estimates?.[f.fundCode];
        const changePct = est?.estimatedChangePct ?? f.dailyChangePct ?? null;
        const isEstimated = !!est;
        return {
            key: f.id,
            id: f.id,
            fundCode: f.fundCode,
            fundName: f.fundName,
            fundSubType: f.fundSubType,
            changePct,
            isEstimated,
            estimateTime: est?.estimateTime,
            // ETF/INDEX 类有成交额字段(暂用 placeholder,后端尚未返回成交额;展示持仓份额代替)
            shares: f.shares,
            status: f.status,
        };
    });

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
            render: (v, r) => (
                <span className="watchlist-name-cell">
                    <span className="watchlist-name-text" title={v}>{v}</span>
                    {r.status === 'HOLDING' && <span className="holding-dot" title="持仓" aria-hidden="true"/>}
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
            dataIndex: 'shares',
            width: 128,
            align: 'right',
            responsive: ['sm'],
            render: (v) => v ? <span className="num-cell">{compactMoney(v)}</span> : <span className="muted">-</span>,
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
