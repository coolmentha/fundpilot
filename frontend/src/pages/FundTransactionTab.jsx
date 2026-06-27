import {Card, Table, Typography, Button, Popconfirm} from 'antd';
import {useFundTransactions, useCancelTransaction} from '../api/hooks.js';
import {datetime, money} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';

const {Title} = Typography;

/**
 * 基金详情 · 交易流水 tab(issue #18 交易合并到基金详情)。
 * 列出该基金全部交易(按时间倒序),PENDING 行内嵌撤单,复用 POST /api/transactions/{id}/cancel。
 */
export default function FundTransactionTab({fundId}) {
    const {data: transactions, isLoading} = useFundTransactions(fundId);
    const cancelTx = useCancelTransaction();

    const columns = [
        {title: '日期', dataIndex: 'createdDate', width: 170, render: datetime},
        {title: '来源', dataIndex: 'source', width: 110, render: (v) => <StatusTag value={v}/>},
        {title: '金额', dataIndex: 'amount', width: 140, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{money(v)}</span>},
        {title: '份额', dataIndex: 'shares', width: 120, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{Number(v).toFixed(2)}</span>},
        {title: '净值', dataIndex: 'nav', width: 100, align: 'right',
            render: (v) => v == null ? '-' : <span className="num-cell">{Number(v).toFixed(4)}</span>},
        {title: '状态', dataIndex: 'status', width: 110, render: (v) => <StatusTag value={v}/>},
        {
            title: '', width: 90, render: (_, r) => r.status === 'PENDING' && (
                <Popconfirm title="撤单该笔交易?" onConfirm={() => cancelTx.mutate(r.id)}>
                    <Button type="link" size="small" danger loading={cancelTx.isPending}>撤单</Button>
                </Popconfirm>
            ),
        },
    ];

    return (
        <Card title={<Title level={5}>交易流水</Title>}>
            <Table rowKey="id" size="small" loading={isLoading} dataSource={transactions}
                   columns={columns} pagination={{pageSize: 10, size: 'small'}} scroll={{x: 820}}
                   locale={{emptyText: <EmptyState description="暂无交易"/>}}/>
        </Card>
    );
}
