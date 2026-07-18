import {App, Button, Card, Popconfirm, Space, Table, Typography} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {
    useCancelTransaction,
    useConfirmTransaction,
    useFunds,
    usePendingTransactions,
} from '../api/hooks.js';
import {datetime, money} from '../constants.js';
import StatusTag from '../components/StatusTag.jsx';
import EmptyState from '../components/EmptyState.jsx';
import QueryErrorState from '../components/QueryErrorState.jsx';
import PendingTransactionEditModal from '../components/PendingTransactionEditModal.jsx';
import {canEditPendingTransaction} from '../transactionEditing.js';

const {Title, Text} = Typography;

export default function ConfirmPage() {
    const {message} = App.useApp();
    const {data: transactions, isLoading, isError, refetch} = usePendingTransactions();
    const {data: funds} = useFunds();
    const confirmTx = useConfirmTransaction();
    const cancelTx = useCancelTransaction();
    const [editing, setEditing] = useState(null);
    const fundName = (id) => funds?.find((fund) => fund.id === id)?.fundName || `基金 #${id}`;

    const confirm = async (id) => {
        await confirmTx.mutateAsync(id);
        message.success('交易已确认');
    };
    const cancel = async (id) => {
        await cancelTx.mutateAsync(id);
        message.success('交易已撤销');
    };

    const columns = [
        {title: '交易日期', dataIndex: 'tradeDate', width: 170, render: datetime},
        {title: '基金', width: 220, render: (_, row) => (
            <Link to={`/funds/${row.fundId}`}>{fundName(row.fundId)}</Link>
        )},
        {title: '来源', dataIndex: 'source', width: 110, render: (value) => <StatusTag value={value}/>},
        {title: '金额', dataIndex: 'amount', width: 140, align: 'right',
            render: (value) => value == null ? '-' : <span className="num-cell">{money(value)}</span>},
        {title: '份额', dataIndex: 'shares', width: 120, align: 'right',
            render: (value) => value == null ? '-' : <span className="num-cell">{Number(value).toFixed(2)}</span>},
        {title: '状态', dataIndex: 'status', width: 100, align: 'right',
            render: (value) => <StatusTag value={value}/>},
        {title: '操作', width: 180, align: 'right', render: (_, row) => (
            <Space size={0}>
                {canEditPendingTransaction(row) && (
                    <Button type="link" size="small" onClick={() => setEditing(row)}>编辑</Button>
                )}
                <Popconfirm title="确认该笔交易？" description="按交易发生日净值回填并计入持仓"
                            onConfirm={() => confirm(row.id)}>
                    <Button type="link" size="small" loading={confirmTx.isPending}>确认</Button>
                </Popconfirm>
                <Popconfirm title="撤销该笔交易？" onConfirm={() => cancel(row.id)}>
                    <Button type="link" size="small" danger loading={cancelTx.isPending}>撤单</Button>
                </Popconfirm>
            </Space>
        )},
    ];

    return (
        <Card title={<Title level={4}>操作确认</Title>} extra={
            <Button icon={<ReloadOutlined/>} onClick={() => refetch()}>刷新</Button>
        }>
            <Text type="secondary" style={{display: 'block', marginBottom: 16}}>
                汇总所有基金待净值确认的交易，可手动确认或撤销。
            </Text>
            {isError ? <QueryErrorState onRetry={refetch} description="待处理交易加载失败"/> : (
                <Table rowKey="id" size="small" loading={isLoading} dataSource={transactions || []}
                       columns={columns} pagination={false} scroll={{x: 990}}
                       locale={{emptyText: <EmptyState description="暂无待处理交易"/>}}/>
            )}
            {editing && (
                <PendingTransactionEditModal transaction={editing}
                                             holdingShares={funds?.find((fund) => fund.id === editing.fundId)?.holdingShares}
                                             onClose={() => setEditing(null)}/>
            )}
        </Card>
    );
}
