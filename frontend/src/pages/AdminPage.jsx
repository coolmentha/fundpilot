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
        } catch {
            // 错误由全局 mutation onError 弹 notification,这里不重复提示
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
                        <Popconfirm title="生成今日信号？未回应信号将按最新行情重算。" onConfirm={() =>
                            run('generate', () => '信号生成完成')}>
                            <Button type="primary" icon={<ThunderboltOutlined/>}
                                    loading={adminAction.isPending}>生成今日信号</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">每日 14:50 自动触发；手动重跑会更新未回应信号，已生成交易的信号保持不变。</Text>
                    </Card>
                    <Card size="small" title="净值确认" extra={
                        <Popconfirm title="按每笔交易发生日补偿确认 PENDING 交易？" onConfirm={() =>
                            run('confirm-nav', (r) => `净值确认完成，回填 ${r?.confirmed ?? 0} 条`)}>
                            <Button icon={<DatabaseOutlined/>}
                                    loading={adminAction.isPending}>回填净值</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">按每笔交易发生日查累计净值；净值新增、启动补偿和定时补偿都会自动推进确认。</Text>
                    </Card>
                    <Card size="small" title="行情刷新" extra={
                        <Popconfirm title="全量刷新行情数据？" onConfirm={() =>
                            run('refresh', () => '行情刷新完成')}>
                            <Button icon={<ReloadOutlined/>}
                                    loading={adminAction.isPending}>刷新行情</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">通过行情数据源降级链刷新所有未归档基金的净值和指标快照。</Text>
                    </Card>
                    <Card size="small" title="基金字典同步" extra={
                        <Popconfirm title="拉取全量基金字典并更新本地缓存？" onConfirm={() =>
                            run('sync-dict', (r) => `字典同步完成，更新 ${r?.upserted ?? 0} 条，回填 ${r?.backfilled ?? 0} 只基金`)}>
                            <Button icon={<DatabaseOutlined/>}
                                    loading={adminAction.isPending}>同步字典</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">每日 03:00 自动触发，拉东方财富全量字典 upsert 到 fund_dict 表，供新建基金搜索框自动补全。</Text>
                    </Card>
                    <Card size="small" title="交易日历同步" extra={
                        <Popconfirm title="从东方财富同步 A 股交易日历？" onConfirm={() =>
                            run('sync-calendar', (r) => `交易日历同步完成，新增 ${r?.added ?? 0} 条`)}>
                            <Button icon={<DatabaseOutlined/>}
                                    loading={adminAction.isPending}>同步交易日历</Button>
                        </Popconfirm>
                    }>
                        <Text type="secondary">从上证指数日K线提取交易日(周末节假日自动跳过)，写入 trading_calendar 表，供 MIN_HOLD_DAYS 判定。</Text>
                    </Card>
                </Space>
            </Card>
        </Space>
    );
}
