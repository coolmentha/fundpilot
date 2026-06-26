import {App, Button, Card, Popconfirm, Space, Typography} from 'antd';
import {ThunderboltOutlined, ReloadOutlined, DatabaseOutlined} from '@ant-design/icons';
import {useAdminAction} from '../api/hooks.js';

const {Title, Text} = Typography;

export default function AdminPage() {
    const {message} = App.useApp();
    const adminAction = useAdminAction();

    const run = async (action, successMsg) => {
        try {
            const result = await adminAction.mutateAsync(action);
            message.success(successMsg(result));
        } catch (e) {
            message.error(e.message);
        }
    };

    return (
        <Space direction="vertical" size={16} className="full-width">
            <Card title={<Title level={4}>管理操作</Title>}>
                <Text type="secondary" style={{display: 'block', marginBottom: 24}}>
                    手动触发定时任务（日常由后端 @Scheduled 自动执行，此处用于调试/补跑）。
                </Text>
                <Space direction="vertical" size="middle" className="full-width">
                    <Card size="small" title="信号生成" extra={
                        <Popconfirm title="生成今日信号？将覆盖当日已有信号。" onConfirm={() =>
                            run('generate', () => '信号生成完成')}>
                            <Button type="primary" icon={<ThunderboltOutlined/>}
                                    loading={adminAction.isPending}>生成今日信号</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">每日 14:50 自动触发，遍历 EFFECTIVE 基金跑信号引擎落 SignalLog。</Text>
                    </Card>
                    <Card size="small" title="净值确认" extra={
                        <Popconfirm title="回填今日 PENDING 交易净值？" onConfirm={() =>
                            run('confirm-nav', (r) => `净值确认完成，回填 ${r?.confirmed ?? 0} 条`)}>
                            <Button icon={<DatabaseOutlined/>}
                                    loading={adminAction.isPending}>回填净值</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">每日 21:00 自动触发，回填当日 PENDING 交易的 nav + 份额/金额，转 CONFIRMED。</Text>
                    </Card>
                    <Card size="small" title="行情刷新" extra={
                        <Popconfirm title="全量刷新行情数据？" onConfirm={() =>
                            run('refresh', () => '行情刷新完成')}>
                            <Button icon={<ReloadOutlined/>}
                                    loading={adminAction.isPending}>刷新行情</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">从东方财富拉取所有 EFFECTIVE 基金的行情指标快照。</Text>
                    </Card>
                </Space>
            </Card>
        </Space>
    );
}
