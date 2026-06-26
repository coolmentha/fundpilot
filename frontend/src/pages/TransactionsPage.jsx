import {useState} from 'react';
import {App, Button, Card, Input, Popconfirm, Space, Typography} from 'antd';
import {useCancelTransaction} from '../api/hooks.js';

const {Title, Text} = Typography;

/**
 * 交易管理：本期后端未提供交易列表/单条查询端点（仅有 POST /api/transactions/{id}/cancel）。
 * 故此处不展示流水列表，仅保留"输入交易 ID 撤单"功能。交易 ID 从「操作确认」生成交易后获得。
 */
export default function TransactionsPage() {
    const {message} = App.useApp();
    const cancelTx = useCancelTransaction();
    const [txId, setTxId] = useState('');

    const doCancel = async () => {
        const id = txId.trim();
        if (!id) return;
        try {
            await cancelTx.mutateAsync(id);
            message.success('交易已撤销');
            setTxId('');
        } catch (e) {
            message.error(e.message);
        }
    };

    return (
        <Card title={<Title level={4}>交易管理</Title>}>
            <Text type="secondary" style={{display: 'block', marginBottom: 16}}>
                后端本期未提供交易流水列表与单条查询端点。在「操作确认」生成交易后，可在此输入交易 ID 执行撤单（PENDING → CANCELLED）。
            </Text>
            <Space>
                <Input placeholder="交易 ID" value={txId}
                       onChange={(e) => setTxId(e.target.value)} style={{width: 240}}/>
                <Popconfirm title={`撤销交易 ${txId.trim()}？`} onConfirm={doCancel}
                            disabled={!txId.trim()}>
                    <Button type="primary" danger disabled={!txId.trim()}
                            loading={cancelTx.isPending}>撤单</Button>
                </Popconfirm>
            </Space>
        </Card>
    );
}
